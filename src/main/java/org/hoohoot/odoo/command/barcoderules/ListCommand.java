package org.hoohoot.odoo.command.barcoderules;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import org.hoohoot.odoo.format.CsvFormatter;
import org.hoohoot.odoo.format.PrettyFormatter;
import org.hoohoot.odoo.model.BarcodeRule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "list",
        description = "Liste les règles de code-barres de la nomenclature par défaut",
        mixinStandardHelpOptions = true
)
public class ListCommand implements Callable<Integer> {

    public enum OutputFormat { pretty, csv }

    public enum SortBy { nom, type, encodage, modele, date, sequence, transformer }

    public enum SortDirection { asc, desc }

    private static final DateTimeFormatter ODOO_DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DISPLAY_DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Option(
            names = "--output",
            paramLabel = "FORMAT",
            description = "Format de sortie: ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE})",
            defaultValue = "pretty"
    )
    OutputFormat output;

    @Option(
            names = "--sort-by",
            paramLabel = "COLUMN",
            description = "Colonne de tri : ${COMPLETION-CANDIDATES} (défaut: sequence)",
            defaultValue = "sequence"
    )
    SortBy sortBy;

    @Option(
            names = "--sort-direction",
            paramLabel = "DIR",
            description = "Sens du tri : ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE})",
            defaultValue = "asc"
    )
    SortDirection sortDirection;

    @Inject
    OdooClient odoo;

    @Inject
    PrettyFormatter pretty;

    @Inject
    CsvFormatter csv;

    @Override
    public Integer call() {
        Integer nomenclatureId = findDefaultNomenclatureId();
        if (nomenclatureId == null) {
            System.err.println("Aucune nomenclature de code-barres trouvée");
            return 1;
        }

        List<BarcodeRule> rules = fetchRules(nomenclatureId);
        rules.sort(buildComparator());

        switch (output) {
            case csv -> printCsv(rules);
            case pretty -> printPretty(rules);
        }
        System.err.printf("%d règle(s)%n", rules.size());
        return 0;
    }

    private Comparator<BarcodeRule> buildComparator() {
        Comparator<BarcodeRule> cmp = switch (sortBy) {
            case nom -> Comparator.comparing(BarcodeRule::name, String.CASE_INSENSITIVE_ORDER);
            case type -> Comparator.comparing(BarcodeRule::type, String.CASE_INSENSITIVE_ORDER);
            case encodage -> Comparator.comparing(BarcodeRule::encoding, String.CASE_INSENSITIVE_ORDER);
            case modele -> Comparator.comparing(BarcodeRule::pattern, String.CASE_INSENSITIVE_ORDER);
            case date -> Comparator.comparing(BarcodeRule::createDate, Comparator.nullsLast(Comparator.naturalOrder()));
            case sequence -> Comparator.comparingInt(BarcodeRule::sequence);
            case transformer -> Comparator.comparing(BarcodeRule::transformExpr, String.CASE_INSENSITIVE_ORDER);
        };
        return sortDirection == SortDirection.desc ? cmp.reversed() : cmp;
    }

    private Integer findDefaultNomenclatureId() {
        JsonNode rows = odoo.searchRead("barcode.nomenclature", List.of(), List.of("id", "name"));
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return null;
        }
        return rows.get(0).get("id").asInt();
    }

    private List<BarcodeRule> fetchRules(int nomenclatureId) {
        List<Object> domain = List.of(
                List.of("barcode_nomenclature_id", "=", nomenclatureId)
        );
        List<String> fields = List.of(
                "id", "name", "type", "encoding", "pattern", "sequence", "create_date", "transform_expr"
        );
        JsonNode rows = odoo.searchRead("barcode.rule", domain, fields);
        if (rows == null || !rows.isArray() || rows.isEmpty()) {
            return new ArrayList<>();
        }
        List<BarcodeRule> result = new ArrayList<>();
        for (JsonNode r : rows) {
            result.add(new BarcodeRule(
                    r.get("id").asInt(),
                    textOrEmpty(r, "name"),
                    textOrEmpty(r, "type"),
                    textOrEmpty(r, "encoding"),
                    textOrEmpty(r, "pattern"),
                    textOrEmpty(r, "create_date"),
                    r.path("sequence").asInt(0),
                    textOrEmpty(r, "transform_expr")
            ));
        }
        return result;
    }

    private void printCsv(List<BarcodeRule> rules) {
        csv.print(
                new String[]{"Nom", "Type", "Encodage", "Modèle", "Date création", "Séquence", "Transformer"},
                toRows(rules)
        );
    }

    private void printPretty(List<BarcodeRule> rules) {
        pretty.print(
                new String[]{"Nom", "Type", "Encodage", "Modèle", "Date création", "Séquence", "Transformer"},
                toRows(rules),
                new boolean[]{false, false, false, false, false, true, false}
        );
    }

    private static String[][] toRows(List<BarcodeRule> rules) {
        String[][] rows = new String[rules.size()][];
        for (int i = 0; i < rules.size(); i++) {
            BarcodeRule r = rules.get(i);
            rows[i] = new String[]{
                    r.name(),
                    r.type(),
                    r.encoding(),
                    r.pattern(),
                    formatCreateDate(r.createDate()),
                    String.valueOf(r.sequence()),
                    r.transformExpr()
            };
        }
        return rows;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isBoolean()) {
            return "";
        }
        return v.asText("");
    }

    private static String formatCreateDate(String odooDatetime) {
        if (odooDatetime == null || odooDatetime.isBlank()) {
            return "";
        }
        try {
            LocalDateTime dt = LocalDateTime.parse(odooDatetime, ODOO_DATETIME_FMT);
            return dt.toLocalDate().format(DISPLAY_DATE_FMT);
        } catch (Exception ignore) {
            try {
                return LocalDate.parse(odooDatetime).format(DISPLAY_DATE_FMT);
            } catch (Exception ignore2) {
                return odooDatetime;
            }
        }
    }
}
