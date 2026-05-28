package org.hoohoot.odoo.command.cooperators;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "reset-ftop-counter",
        description = "Remet à 0 les compteurs FTOP des coopérateurs : neutralise les shift.counter.event ftop (ignored=true, statut/final_ftop_point) puis crée un event compensateur par coop pour ramener le compteur affiché (display_ftop_points) à 0, avec le motif « Services offerts jusque septembre 2026 »",
        mixinStandardHelpOptions = true
)
public class ResetFtopCounterCommand implements Callable<Integer> {

    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private static final int REASON_SERVICES_OFFERTS = 1;
    private static final String COMPENSATION_NAME = "Remise à zéro compteur FTOP (services offerts)";

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

        String prefix = dryRun ? "[dry-run] " : "";

        int ignoredCount = neutralizeEvents(before, prefix);
        int resetCount = zeroDisplayCounters(prefix);

        if (dryRun) {
            System.err.printf("Mode dry-run : aucune modification effectuée. %d event(s) FTOP et %d compteur(s) simulé(s)%n",
                    ignoredCount, resetCount);
        } else {
            System.err.printf("%d event(s) FTOP neutralisé(s) ; %d compteur(s) FTOP remis à 0%n",
                    ignoredCount, resetCount);
        }
        return 0;
    }

    /**
     * Étape 1 — marque ignored=true sur les events ftop (avec motif), ce qui pilote
     * final_ftop_point et donc le statut du membre. Renvoie le nombre d'events neutralisés.
     */
    private int neutralizeEvents(LocalDate before, String prefix) {
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
            return 0;
        }

        List<Integer> eventIds = new ArrayList<>();
        for (JsonNode ev : events) {
            int eid = ev.get("id").asInt();
            JsonNode pref = ev.get("partner_id");
            String pname = pref.get(1).asText();
            double pts = ev.path("point_qty").asDouble(0);
            JsonNode sref = ev.get("shift_id");
            String sname = sref != null && sref.isArray() && !sref.isEmpty() ? sref.get(1).asText() : "";
            String evName = ev.path("name").asText("");
            eventIds.add(eid);

            System.out.printf("%sevent %d : %s — %s (%s, %+.1f pt)%n",
                    prefix, eid, pname, sname, evName, pts);
        }

        if (!dryRun) {
            odoo.executeKw("shift.counter.event", "write",
                    List.of(eventIds, Map.of(
                            "ignored", true,
                            "reason_ids", List.of(List.of(4, REASON_SERVICES_OFFERTS))
                    )));
        }
        return eventIds.size();
    }

    /**
     * Étape 2 — display_ftop_points (le compteur affiché) compte TOUS les events, ignorés
     * compris : ignored=true ne le bouge donc pas. Pour chaque coop au solde ftop positif,
     * on crée un event compensateur (point_qty = -display, lui aussi ignored) qui ramène
     * display_ftop_points à 0 sans rendre final_ftop_point négatif. Renvoie le nombre de
     * compteurs remis à 0.
     */
    private int zeroDisplayCounters(String prefix) {
        List<Object> domain = new ArrayList<>();
        domain.add(List.of("display_ftop_points", ">", 0));
        if (partnerIds != null && partnerIds.length > 0) {
            domain.add(List.of("id", "in", List.of(partnerIds)));
        }
        JsonNode partners = odoo.searchRead("res.partner", domain,
                List.of("id", "name", "display_ftop_points"));

        if (partners == null || !partners.isArray() || partners.isEmpty()) {
            return 0;
        }

        int resetCount = 0;
        for (JsonNode p : partners) {
            int pid = p.get("id").asInt();
            String pname = p.path("name").asText("");
            double display = p.path("display_ftop_points").asDouble(0);

            System.out.printf("%scompteur %s : %.1f → 0 (compensation %+.1f pt)%n",
                    prefix, pname, display, -display);

            if (!dryRun) {
                odoo.executeKw("shift.counter.event", "create",
                        List.of(Map.of(
                                "name", COMPENSATION_NAME,
                                "type", "ftop",
                                "partner_id", pid,
                                "point_qty", -display,
                                "ignored", true,
                                "reason_ids", List.of(List.of(4, REASON_SERVICES_OFFERTS))
                        )));
            }
            resetCount++;
        }
        return resetCount;
    }
}
