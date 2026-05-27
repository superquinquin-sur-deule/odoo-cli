package org.hoohoot.odoo.command.cooperators;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "reset-ftop-counter",
        description = "Remet à 0 les compteurs FTOP des coopérateurs en marquant ignored=true sur tous les shift.counter.event de type ftop, avec le motif « Services avant ouverture »",
        mixinStandardHelpOptions = true
)
public class ResetFtopCounterCommand implements Callable<Integer> {

    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final int REASON_SERVICES_AVANT_OUVERTURE = 1;

    @Option(
            names = "--before-date",
            paramLabel = "DATE",
            description = "Ne traite que les events dont create_date <= DATE 23:59:59 (jj/MM/aaaa)"
    )
    String beforeDate;

    @Option(
            names = "--partner-id",
            paramLabel = "ID",
            description = "Filtre par id de coopérateur (répétable) ; ne traite que les events de ces partners"
    )
    Integer[] partnerIds;

    @Option(
            names = "--dry-run",
            description = "Simule l'exécution sans écrire dans Odoo"
    )
    boolean dryRun;

    @Inject
    OdooClient odoo;

    @Override
    public Integer call() {
        LocalDate before = null;
        if (beforeDate != null && !beforeDate.isBlank()) {
            try {
                before = LocalDate.parse(beforeDate, INPUT_DATE_FORMAT);
            } catch (Exception e) {
                System.err.println(beforeDate + " : mauvais format de date (jj/MM/aaaa) pour --before-date");
                return 2;
            }
        }

        List<Object> domain = new ArrayList<>();
        domain.add(List.of("type", "=", "ftop"));
        domain.add(List.of("ignored", "=", false));
        if (before != null) {
            domain.add(List.of("create_date", "<=", before.format(ODOO_DATE_FORMAT) + " 23:59:59"));
        }
        if (partnerIds != null && partnerIds.length > 0) {
            domain.add(List.of("partner_id", "in", List.of(partnerIds)));
        }
        JsonNode events = odoo.searchRead("shift.counter.event", domain,
                List.of("id", "name", "point_qty", "partner_id", "shift_id", "create_date"));

        if (events == null || !events.isArray() || events.isEmpty()) {
            System.err.printf("0 event(s) FTOP à neutraliser%s%n",
                    before != null ? " avant le " + beforeDate : "");
            return 0;
        }

        Map<Integer, String> partnerNames = new LinkedHashMap<>();
        Map<Integer, Double> pointsByPartner = new LinkedHashMap<>();
        List<Integer> eventIds = new ArrayList<>();
        String prefix = dryRun ? "[dry-run] " : "";
        for (JsonNode ev : events) {
            int eid = ev.get("id").asInt();
            JsonNode pref = ev.get("partner_id");
            int pid = pref.get(0).asInt();
            String pname = pref.get(1).asText();
            double pts = ev.path("point_qty").asDouble(0);
            JsonNode sref = ev.get("shift_id");
            String sname = sref != null && sref.isArray() && !sref.isEmpty() ? sref.get(1).asText() : "";
            String evName = ev.path("name").asText("");

            partnerNames.put(pid, pname);
            pointsByPartner.merge(pid, pts, Double::sum);
            eventIds.add(eid);

            System.out.printf("%sevent %d : %s — %s (%s, %+.1f pt)%n",
                    prefix, eid, pname, sname, evName, pts);
        }

        if (!dryRun) {
            odoo.executeKw("shift.counter.event", "write",
                    List.of(eventIds, Map.of(
                            "ignored", true,
                            "reason_ids", List.of(List.of(4, REASON_SERVICES_AVANT_OUVERTURE))
                    )));
        }

        if (dryRun) {
            System.err.printf("Mode dry-run : aucune modification effectuée. %d event(s) FTOP simulé(s) pour %d coopérateur(s)%n",
                    eventIds.size(), partnerNames.size());
        } else {
            System.err.printf("%d event(s) FTOP neutralisé(s) pour %d coopérateur(s)%n",
                    eventIds.size(), partnerNames.size());
        }
        return 0;
    }
}
