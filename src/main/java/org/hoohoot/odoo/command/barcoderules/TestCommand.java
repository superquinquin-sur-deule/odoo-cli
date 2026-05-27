package org.hoohoot.odoo.command.barcoderules;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import org.hoohoot.odoo.format.CsvFormatter;
import org.hoohoot.odoo.format.PrettyFormatter;
import org.hoohoot.odoo.model.BarcodeRule;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(
        name = "test",
        description = "Teste un code-barres contre les règles de la nomenclature par défaut (premier match par séquence)",
        mixinStandardHelpOptions = true
)
public class TestCommand implements Callable<Integer> {

    public enum OutputFormat { pretty, csv }

    @Parameters(index = "0", paramLabel = "BARCODE", description = "Code-barres à tester")
    String barcode;

    @Option(
            names = "--output",
            paramLabel = "FORMAT",
            description = "Format de sortie: ${COMPLETION-CANDIDATES} (défaut: ${DEFAULT-VALUE})",
            defaultValue = "pretty"
    )
    OutputFormat output;

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
        rules.sort(Comparator.comparingInt(BarcodeRule::sequence));

        List<MatchResult> results = new ArrayList<>(rules.size());
        Integer appliedIdx = null;
        for (int i = 0; i < rules.size(); i++) {
            MatchResult m = matchRule(barcode, rules.get(i));
            results.add(m);
            if (m.match && appliedIdx == null) {
                appliedIdx = i;
            }
        }

        printResults(rules, results, appliedIdx);

        if (appliedIdx != null) {
            BarcodeRule applied = rules.get(appliedIdx);
            String value = results.get(appliedIdx).value;
            System.err.printf("Règle appliquée : %s (séquence %d, type=%s)%s%n",
                    applied.name(), applied.sequence(), applied.type(),
                    value.isEmpty() ? "" : (" — valeur=" + value));
        } else {
            System.err.println("Aucune règle applicable");
        }
        return 0;
    }

    private void printResults(List<BarcodeRule> rules, List<MatchResult> results, Integer appliedIdx) {
        String[] headers = {"Séq.", "Nom", "Type", "Encodage", "Modèle", "Match", "Valeur"};
        String[][] rows = new String[rules.size()][];
        int idxApplied = appliedIdx == null ? -1 : appliedIdx;
        for (int i = 0; i < rules.size(); i++) {
            BarcodeRule r = rules.get(i);
            MatchResult m = results.get(i);
            String mark;
            if (i == idxApplied) {
                mark = "→";
            } else if (m.match) {
                mark = "✓";
            } else {
                mark = "✗";
            }
            rows[i] = new String[]{
                    String.valueOf(r.sequence()),
                    r.name(),
                    r.type(),
                    r.encoding(),
                    r.pattern(),
                    mark,
                    m.value
            };
        }
        switch (output) {
            case csv -> csv.print(headers, rows);
            case pretty -> pretty.print(headers, rows,
                    new boolean[]{true, false, false, false, false, false, false});
        }
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

    static MatchResult matchRule(String barcode, BarcodeRule rule) {
        MatchResult res = new MatchResult();
        if (!checkEncoding(barcode, rule.encoding())) {
            return res;
        }
        String pattern = rule.pattern();
        if (pattern == null || pattern.isEmpty()) {
            return res;
        }
        Matcher num = NUMERICAL_CONTENT.matcher(pattern);
        if (num.find()) {
            int numStart = num.start();
            int numEnd = num.end();
            String nPart = num.group(1);
            String dPart = num.group(2);
            int valLen = nPart.length() + dPart.length();
            if (barcode.length() < numStart + valLen) {
                return res;
            }
            String prefix = pattern.substring(0, numStart);
            if (!prefix.isEmpty()) {
                String prefixRegex = translateOdooPatternToRegex(prefix);
                if (!Pattern.compile(prefixRegex).matcher(barcode.substring(0, numStart)).matches()) {
                    return res;
                }
            }
            String valStr = barcode.substring(numStart, numStart + valLen);
            if (!valStr.matches("\\d+")) {
                return res;
            }
            String suffix = pattern.substring(numEnd);
            if (!suffix.isEmpty()) {
                String remaining = barcode.substring(numStart + valLen);
                String suffixRegex = translateOdooPatternToRegex(suffix);
                if (!Pattern.compile(suffixRegex).matcher(remaining).lookingAt()) {
                    return res;
                }
            }
            String intPart = nPart.isEmpty() ? "0" : valStr.substring(0, nPart.length());
            String decPart = dPart.isEmpty() ? "" : valStr.substring(nPart.length());
            if (decPart.isEmpty()) {
                res.value = String.valueOf(Long.parseLong(intPart));
            } else {
                long intVal = Long.parseLong(intPart);
                res.value = intVal + "." + decPart;
            }
            res.match = true;
            return res;
        }
        String regex = translateOdooPatternToRegex(pattern);
        Matcher m = Pattern.compile(regex).matcher(barcode);
        if (m.lookingAt()) {
            res.match = true;
        }
        return res;
    }

    static String translateOdooPatternToRegex(String pattern) {
        return pattern;
    }

    static boolean checkEncoding(String barcode, String encoding) {
        if (encoding == null || encoding.isEmpty() || encoding.equals("any")) {
            return true;
        }
        if (encoding.equals("ean13")) {
            return barcode.length() == 13
                    && DIGITS.matcher(barcode).matches()
                    && ean13Checksum(barcode) == (barcode.charAt(12) - '0');
        }
        if (encoding.equals("ean8")) {
            return barcode.length() == 8
                    && DIGITS.matcher(barcode).matches()
                    && ean8Checksum(barcode) == (barcode.charAt(7) - '0');
        }
        if (encoding.equals("upca")) {
            return barcode.length() == 12
                    && DIGITS.matcher(barcode).matches()
                    && ean13Checksum("0" + barcode) == (barcode.charAt(11) - '0');
        }
        return true;
    }

    static int ean13Checksum(String barcode) {
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = barcode.charAt(i) - '0';
            sum += (i % 2 == 0) ? d : d * 3;
        }
        return (10 - sum % 10) % 10;
    }

    static int ean8Checksum(String barcode) {
        int sum = 0;
        for (int i = 0; i < 7; i++) {
            int d = barcode.charAt(i) - '0';
            sum += (i % 2 == 0) ? d * 3 : d;
        }
        return (10 - sum % 10) % 10;
    }

    private static String textOrEmpty(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || v.isBoolean()) {
            return "";
        }
        return v.asText("");
    }

    private static final Pattern NUMERICAL_CONTENT = Pattern.compile("\\{(N*)(D*)\\}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");

    static class MatchResult {
        boolean match;
        String value = "";
    }
}
