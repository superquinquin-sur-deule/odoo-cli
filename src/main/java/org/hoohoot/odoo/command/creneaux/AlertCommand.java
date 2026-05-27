package org.hoohoot.odoo.command.creneaux;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import org.hoohoot.odoo.format.CsvFormatter;
import org.hoohoot.odoo.format.JsonFormatter;
import org.hoohoot.odoo.format.PrettyFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "alert",
        description = "Liste les services (shift.shift) sous le minimum entre maintenant et J+1 23:59",
        mixinStandardHelpOptions = true
)
public class AlertCommand implements Callable<Integer> {

    private static final DateTimeFormatter ODOO_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter INPUT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public enum OutputFormat { pretty, csv, json }

    @Option(
            names = "--output",
            paramLabel = "FORMAT",
            description = "Format de sortie: ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE})",
            defaultValue = "pretty"
    )
    OutputFormat output;

    @Option(
            names = "--begin-date",
            paramLabel = "DATE",
            description = "Date de début (jj/MM/aaaa) ; défaut: maintenant"
    )
    String beginDate;

    @Option(
            names = "--end-date",
            paramLabel = "DATE",
            description = "Date de fin (jj/MM/aaaa) ; défaut: J+1 23:59"
    )
    String endDate;

    @Inject
    OdooClient odoo;

    @Inject
    PrettyFormatter pretty;

    @Inject
    CsvFormatter csv;

    @Inject
    JsonFormatter json;

    @Override
    public Integer call() {
        ZoneId zone = ZoneId.systemDefault();
        Instant begin;
        Instant end;

        if (beginDate == null) {
            begin = Instant.now();
        } else {
            try {
                begin = LocalDate.parse(beginDate, INPUT_DATE_FORMAT)
                        .atStartOfDay(zone)
                        .toInstant();
            } catch (Exception e) {
                System.err.println(beginDate + " : mauvais format de date (jj/MM/aaaa) pour --begin-date");
                return 2;
            }
        }

        if (endDate == null) {
            end = LocalDateTime.of(LocalDate.now(zone).plusDays(1), LocalTime.of(23, 59, 59))
                    .atZone(zone)
                    .toInstant();
        } else {
            try {
                end = LocalDateTime.of(LocalDate.parse(endDate, INPUT_DATE_FORMAT), LocalTime.of(23, 59, 59))
                        .atZone(zone)
                        .toInstant();
            } catch (Exception e) {
                System.err.println(endDate + " : mauvais format de date (jj/MM/aaaa) pour --end-date");
                return 2;
            }
        }

        if (begin.isAfter(end)) {
            System.err.println("--begin-date doit être antérieure ou égale à --end-date");
            return 2;
        }

        String beginUtc = ODOO_DATETIME_FORMAT.format(begin.atZone(ZoneOffset.UTC));
        String endUtc = ODOO_DATETIME_FORMAT.format(end.atZone(ZoneOffset.UTC));

        List<Object> domain = List.of(
                List.of("date_begin", ">=", beginUtc),
                List.of("date_begin", "<=", endUtc),
                List.of("state", "in", List.of("draft", "confirm"))
        );
        JsonNode rows = odoo.searchRead("shift.shift", domain,
                List.of("id", "name", "state", "date_begin", "date_end",
                        "seats_min", "seats_max", "seats_reserved"));

        List<Map<String, Object>> filtered = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode r : rows) {
                int seatsMin = r.path("seats_min").asInt(0);
                int seatsReserved = r.path("seats_reserved").asInt(0);
                if (seatsMin <= 0 || seatsReserved >= seatsMin) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", r.path("id").asInt());
                row.put("name", r.path("name").asText(""));
                row.put("state", r.path("state").asText(""));
                row.put("date_begin", r.path("date_begin").asText(""));
                row.put("date_end", r.path("date_end").asText(""));
                row.put("seats_min", seatsMin);
                row.put("seats_max", r.path("seats_max").asInt(0));
                row.put("seats_reserved", seatsReserved);
                filtered.add(row);
            }
        }

        filtered.sort(Comparator
                .comparing((Map<String, Object> row) -> (String) row.get("date_begin"))
                .thenComparing(row -> (String) row.get("name"), String.CASE_INSENSITIVE_ORDER));

        switch (output) {
            case csv -> csv.print(
                    new String[]{"Id", "Nom", "État", "Début", "Fin", "Min", "Places", "Réservées"},
                    toTable(filtered));
            case pretty -> pretty.print(
                    new String[]{"Id", "Nom", "État", "Début", "Fin", "Min", "Places", "Rés."},
                    toTable(filtered),
                    new boolean[]{true, false, false, false, false, true, true, true});
            case json -> json.print(filtered);
        }

        int total = filtered.size();
        System.err.printf("%d créneau%s sous le minimum%n", total, total <= 1 ? "" : "(x)");
        return 0;
    }

    private static String[][] toTable(List<Map<String, Object>> rows) {
        String[][] table = new String[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            table[i] = new String[]{
                    String.valueOf(r.get("id")),
                    (String) r.get("name"),
                    (String) r.get("state"),
                    (String) r.get("date_begin"),
                    (String) r.get("date_end"),
                    String.valueOf(r.get("seats_min")),
                    String.valueOf(r.get("seats_max")),
                    String.valueOf(r.get("seats_reserved"))
            };
        }
        return table;
    }
}
