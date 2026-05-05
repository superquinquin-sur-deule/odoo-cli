package org.hoohoot.odoo.command.creneaux;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import org.hoohoot.odoo.model.ShiftTemplate;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "list",
        description = "Liste les créneaux (shift.template)",
        mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    public enum OutputFormat { pretty, csv }

    private static final String CSV_SEP = ";";

    @Option(
            names = "--output",
            paramLabel = "FORMAT",
            description = "Format de sortie: ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE})",
            defaultValue = "pretty"
    )
    OutputFormat output;

    @Option(
            names = "--active-only",
            description = "N'afficher que les créneaux actifs (défaut: ${DEFAULT-VALUE})",
            defaultValue = "true",
            negatable = true
    )
    boolean activeOnly;

    @Option(
            names = "--under-min",
            description = "N'afficher que les créneaux où réservées < seats_min (seats_min > 0)"
    )
    boolean underMin;

    @Inject
    OdooClient odoo;

    @Override
    public Integer call() {
        List<ShiftTemplate> templates = fetchTemplates();
        
        if (underMin) {
            templates.removeIf(t -> t.seatsMin() <= 0 || t.seatsReserved() >= t.seatsMin());
        }
        
        templates.sort(Comparator
                .comparing(ShiftTemplate::weekName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ShiftTemplate::startDatetime)
                .thenComparing(ShiftTemplate::name, String.CASE_INSENSITIVE_ORDER));

        switch (output) {
            case csv -> printCsv(templates);
            case pretty -> printPretty(templates);
        }
        
        printTotalStats(templates.size());
        
        return 0;
    }
    
    private void printTotalStats(Integer total) {
        System.err.printf("%d créneau%s%n", total, total == 1 ? "": "(x)");
    }

    private List<ShiftTemplate> fetchTemplates() {
        List<Object> domain = new ArrayList<>();
        if (!activeOnly) {
            domain.add(List.of("active", "in", List.of(true, false)));
        }
        List<String> fields = List.of(
                "id", "name", "week_name",
                "shift_type_id",
                "start_datetime_tz", "end_datetime_tz",
                "duration", "seats_min", "seats_max", "seats_reserved"
        );
        JsonNode rows = odoo.searchRead("shift.template", domain, fields);
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return List.of();
        }
        List<ShiftTemplate> result = new ArrayList<>();
        for (JsonNode r : rows) {
            result.add(new ShiftTemplate(
                    r.get("id").asInt(),
                    textOrEmpty(r, "name"),
                    textOrEmpty(r, "week_name"),
                    relName(r, "shift_type_id"),
                    textOrEmpty(r, "start_datetime_tz"),
                    textOrEmpty(r, "end_datetime_tz"),
                    r.path("duration").asDouble(0),
                    r.path("seats_min").asInt(0),
                    r.path("seats_max").asInt(0),
                    r.path("seats_reserved").asInt(0)
            ));
        }
        return result;
    }

    private void printCsv(List<ShiftTemplate> templates) {
        System.out.println(String.join(CSV_SEP,
                "Id", "Nom", "Semaine", "Type", "Début", "Fin", "Durée", "Min", "Places", "Réservées"));
        for (ShiftTemplate t : templates) {
            System.out.println(String.join(CSV_SEP,
                    String.valueOf(t.id()),
                    csv(t.name()),
                    csv(t.weekName()),
                    csv(t.shiftType()),
                    csv(t.startDatetime()),
                    csv(t.endDatetime()),
                    formatDuration(t.duration()),
                    String.valueOf(t.seatsMin()),
                    String.valueOf(t.seatsMax()),
                    String.valueOf(t.seatsReserved())
            ));
        }
    }

    private void printPretty(List<ShiftTemplate> templates) {
        String[] headers = {"Id", "Nom", "Sem.", "Type", "Début", "Fin", "Durée", "Min", "Places", "Rés."};
        String[][] rows = new String[templates.size()][headers.length];
        for (int i = 0; i < templates.size(); i++) {
            ShiftTemplate t = templates.get(i);
            rows[i] = new String[]{
                    String.valueOf(t.id()),
                    t.name(),
                    t.weekName(),
                    t.shiftType(),
                    t.startDatetime(),
                    t.endDatetime(),
                    formatDuration(t.duration()),
                    String.valueOf(t.seatsMin()),
                    String.valueOf(t.seatsMax()),
                    String.valueOf(t.seatsReserved())
            };
        }

        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        boolean[] rightAlign = {true, false, false, false, false, false, true, true, true, true};
        printRow(headers, widths, rightAlign);
        String[] sep = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            sep[i] = "-".repeat(widths[i]);
        }
        printRow(sep, widths, new boolean[headers.length]);
        for (String[] row : rows) {
            printRow(row, widths, rightAlign);
        }
    }

    private static void printRow(String[] cells, int[] widths, boolean[] rightAlign) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            String fmt = "%" + (rightAlign[i] ? "" : "-") + widths[i] + "s";
            sb.append(String.format(fmt, cells[i]));
            if (i < cells.length - 1) sb.append("  ");
        }
        System.out.println(sb);
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isBoolean()) {
            return "";
        }
        return v.asText("");
    }

    private static String relName(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || !v.isArray() || v.size() < 2) {
            return "";
        }
        return v.get(1).asText("");
    }

    private static String csv(String value) {
        if (value == null) return "";
        if (value.contains(CSV_SEP) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String formatDuration(double duration) {
        if (duration == Math.floor(duration)) {
            return String.valueOf((long) duration);
        }
        return String.format("%.2f", duration);
    }
}
