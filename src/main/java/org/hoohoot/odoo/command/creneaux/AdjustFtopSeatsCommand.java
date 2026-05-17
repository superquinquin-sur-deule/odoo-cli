package org.hoohoot.odoo.command.creneaux;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

@Command(
        name = "adjust-ftop-seats",
        description = "Aligne seats_max FTOP sur (max ABCD du template) - (places ABCD réservées) + 1 pour chaque service confirmé",
        mixinStandardHelpOptions = true
)
public class AdjustFtopSeatsCommand implements Callable<Integer> {

    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ODOO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Option(
            names = "--begin-date",
            paramLabel = "DATE",
            required = true,
            description = "Date de début (jj/MM/aaaa)"
    )
    String beginDate;

    @Option(
            names = "--end-date",
            paramLabel = "DATE",
            required = true,
            description = "Date de fin (jj/MM/aaaa)"
    )
    String endDate;

    @Option(
            names = "--dry-run",
            description = "Simule l'exécution sans écrire dans Odoo"
    )
    boolean dryRun;

    @Option(
            names = "--include-draft",
            description = "Inclure aussi les services en brouillon (state=draft), en plus des confirmés"
    )
    boolean includeDraft;

    @Inject
    OdooClient odoo;

    @Override
    public Integer call() {
        LocalDate begin;
        LocalDate end;

        try {
            begin = LocalDate.parse(beginDate, INPUT_DATE_FORMAT);
        } catch (Exception e) {
            System.err.println(beginDate + " : mauvais format de date (jj/MM/aaaa) pour --begin-date");
            return 2;
        }

        try {
            end = LocalDate.parse(endDate, INPUT_DATE_FORMAT);
        } catch (Exception e) {
            System.err.println(endDate + " : mauvais format de date (jj/MM/aaaa) pour --end-date");
            return 2;
        }

        if (begin.isAfter(end)) {
            System.err.println("--begin-date doit être antérieure ou égale à --end-date");
            return 2;
        }

        List<?> stateClause = includeDraft
                ? List.of("state", "in", List.of("confirm", "draft"))
                : List.of("state", "=", "confirm");
        List<Object> shiftDomain = List.of(
                stateClause,
                List.of("date_begin", ">=", begin.format(ODOO_DATE_FORMAT) + " 00:00:00"),
                List.of("date_begin", "<=", end.format(ODOO_DATE_FORMAT) + " 23:59:59")
        );
        JsonNode shifts = odoo.searchRead("shift.shift", shiftDomain,
                List.of("id", "name", "shift_template_id", "shift_ticket_ids"));

        if (shifts == null || !shifts.isArray() || shifts.isEmpty()) {
            String scope = includeDraft ? "confirmé(s) ou en brouillon" : "confirmé(s)";
            System.err.printf("0 service(s) %s entre %s et %s%n", scope, beginDate, endDate);
            return 0;
        }

        Map<Integer, String> shiftNames = new LinkedHashMap<>();
        Map<Integer, Integer> shiftToTemplate = new LinkedHashMap<>();
        Set<Integer> ticketIds = new LinkedHashSet<>();
        Set<Integer> templateIds = new LinkedHashSet<>();
        for (JsonNode shift : shifts) {
            int sid = shift.get("id").asInt();
            shiftNames.put(sid, shift.get("name").asText());
            JsonNode tmpl = shift.get("shift_template_id");
            if (tmpl != null && tmpl.isArray() && !tmpl.isEmpty()) {
                int tid = tmpl.get(0).asInt();
                shiftToTemplate.put(sid, tid);
                templateIds.add(tid);
            }
            JsonNode ids = shift.get("shift_ticket_ids");
            if (ids != null && ids.isArray()) {
                ids.forEach(n -> ticketIds.add(n.asInt()));
            }
        }

        JsonNode tickets = odoo.searchRead("shift.ticket",
                List.of(List.of("id", "in", new ArrayList<>(ticketIds))),
                List.of("id", "shift_id", "shift_type", "seats_max", "seats_reserved"));

        Map<Integer, Integer> standardReserved = new HashMap<>();
        Map<Integer, Integer> ftopTicketId = new HashMap<>();
        Map<Integer, Integer> ftopCurrentMax = new HashMap<>();
        if (tickets != null && tickets.isArray()) {
            for (JsonNode t : tickets) {
                int sid = t.get("shift_id").get(0).asInt();
                String type = t.get("shift_type").asText();
                if ("standard".equals(type)) {
                    standardReserved.put(sid, t.get("seats_reserved").asInt());
                } else if ("ftop".equals(type)) {
                    ftopTicketId.put(sid, t.get("id").asInt());
                    ftopCurrentMax.put(sid, t.get("seats_max").asInt());
                }
            }
        }

        JsonNode templateTickets = odoo.searchRead("shift.template.ticket",
                List.of(
                        List.of("shift_template_id", "in", new ArrayList<>(templateIds)),
                        List.of("shift_type", "=", "standard")
                ),
                List.of("shift_template_id", "seats_max"));

        Map<Integer, Integer> templateStandardMax = new HashMap<>();
        if (templateTickets != null && templateTickets.isArray()) {
            for (JsonNode tt : templateTickets) {
                int tid = tt.get("shift_template_id").get(0).asInt();
                templateStandardMax.put(tid, tt.get("seats_max").asInt());
            }
        }

        int updates = 0;
        int skipped = 0;
        String prefix = dryRun ? "[dry-run] " : "";
        for (JsonNode shift : shifts) {
            int sid = shift.get("id").asInt();
            String name = shiftNames.get(sid);
            Integer tid = shiftToTemplate.get(sid);
            Integer templateMax = tid == null ? null : templateStandardMax.get(tid);
            Integer reserved = standardReserved.get(sid);
            Integer ftopId = ftopTicketId.get(sid);
            Integer ftopMax = ftopCurrentMax.get(sid);

            if (templateMax == null || reserved == null || ftopId == null || ftopMax == null) {
                System.err.printf("Service %d (%s) : ignoré (ticket standard/ftop ou template manquant)%n", sid, name);
                skipped++;
                continue;
            }

            int newMax = Math.max(0, templateMax - reserved + 1);
            System.out.printf("%sService %d (%s) : seats_max FTOP %d → %d (template ABCD %d - réservées %d + 1)%n",
                    prefix, sid, name, ftopMax, newMax, templateMax, reserved);

            if (!dryRun) {
                odoo.executeKw("shift.ticket", "write",
                        List.of(List.of(ftopId), Map.of("seats_max", newMax)));
            }
            updates++;
        }

        if (dryRun) {
            System.err.printf("Mode dry-run : aucune modification effectuée. %d ajustement(s) simulé(s) entre %s et %s%n",
                    updates, beginDate, endDate);
        } else {
            System.err.printf("%d ajustement(s) effectué(s) entre %s et %s%n", updates, beginDate, endDate);
        }
        if (skipped > 0) {
            System.err.printf("%d service(s) ignoré(s)%n", skipped);
        }
        return 0;
    }
}
