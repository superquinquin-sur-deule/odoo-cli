package org.hoohoot.odoo.command.articles;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@Command(
        name = "update-internal-references",
        description = "Met à jour en masse des champs de product.template (référence interne, catégorie, "
                + "règle de code-barre, base) depuis un CSV (en-tête: "
                + "ExternalId,Name,Category,InternalReference,BarcodeRule,BarcodeBase)",
        mixinStandardHelpOptions = true
)
public class UpdateInternalReferencesCommand implements Callable<Integer> {

    private static final String HEADER =
            "ExternalId,Name,Category,InternalReference,BarcodeRule,BarcodeBase";

    @Option(
            names = "--csv",
            required = true,
            paramLabel = "FILE",
            description = "Fichier CSV à 6 colonnes (en-tête: " + HEADER + ")"
    )
    Path csv;

    @Option(
            names = "--dry-run",
            description = "Simule l'exécution sans écrire dans Odoo"
    )
    boolean dryRun;

    @Inject
    OdooClient odoo;

    private record Row(String externalId, String name, String category, String internalReference,
                       String barcodeRule, String barcodeBase) {
    }

    @Override
    public Integer call() {
        List<Row> rows;
        try {
            rows = readCsv(csv);
        } catch (IOException e) {
            System.err.println("Erreur de lecture du CSV: " + e.getMessage());
            return 2;
        }

        Set<String> productExtIds = rows.stream()
                .map(Row::externalId).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        Set<String> categoryExtIds = rows.stream()
                .map(Row::category).filter(s -> !s.isBlank()).collect(Collectors.toSet());
        Set<String> ruleNames = rows.stream()
                .map(Row::barcodeRule).filter(s -> !s.isBlank()).collect(Collectors.toSet());

        Set<String> allExtIds = new HashSet<>();
        allExtIds.addAll(productExtIds);
        allExtIds.addAll(categoryExtIds);

        Map<String, Integer> externalIdToId = resolveExternalIds(allExtIds);
        Map<String, Integer> barcodeRuleNameToId = resolveBarcodeRules(ruleNames);

        int updated = 0;
        List<Row> notFoundRows = new ArrayList<>();

        for (Row row : rows) {
            if (row.externalId().isBlank()) {
                System.err.println("Ligne sans External ID ignorée : " + row.name());
                notFoundRows.add(row);
                continue;
            }
            Integer productId = externalIdToId.get(row.externalId());
            if (productId == null) {
                System.err.println("Non trouvé : " + row.externalId() + " (" + row.name() + ")");
                notFoundRows.add(row);
                continue;
            }

            Map<String, Object> values = new LinkedHashMap<>();
            if (!row.internalReference().isBlank()) {
                values.put("default_code", row.internalReference());
            }
            if (!row.category().isBlank()) {
                Integer categoryId = externalIdToId.get(row.category());
                if (categoryId == null) {
                    System.err.println("Catégorie inconnue pour " + row.name() + " : " + row.category());
                } else {
                    values.put("categ_id", categoryId);
                }
            }
            if (!row.barcodeRule().isBlank()) {
                Integer ruleId = barcodeRuleNameToId.get(row.barcodeRule());
                if (ruleId == null) {
                    System.err.println("Règle de code-barre inconnue pour " + row.name() + " : " + row.barcodeRule());
                } else {
                    values.put("barcode_rule_id", ruleId);
                }
            }
            if (!row.barcodeBase().isBlank()) {
                try {
                    values.put("barcode_base", Integer.parseInt(row.barcodeBase().trim()));
                } catch (NumberFormatException e) {
                    System.err.println("BarcodeBase invalide pour " + row.name() + " : " + row.barcodeBase());
                }
            }

            if (values.isEmpty()) {
                System.err.println("Aucun champ à mettre à jour pour " + row.name() + ", ligne ignorée");
                continue;
            }

            String changes = formatChanges(values);
            String prefix = dryRun ? "[dry-run] " : "";
            System.out.println(prefix + row.name() + " (id=" + productId + ") -> " + changes);

            if (!dryRun) {
                odoo.executeKw(
                        "product.template",
                        "write",
                        List.of(List.of(productId), values)
                );
            }
            updated++;
        }

        if (!notFoundRows.isEmpty()) {
            System.err.println();
            System.err.println("=== Lignes non mises à jour ===");
            for (Row row : notFoundRows) {
                System.err.println("  [non trouvé] " + row.externalId() + " (" + row.name() + ")");
            }
        }

        if (dryRun) {
            System.err.println("Mode dry-run : aucune modification effectuée");
            System.err.printf(
                    "%d mise(s) à jour simulée(s), %d non trouvée(s)%n",
                    updated, notFoundRows.size()
            );
        } else {
            System.err.printf(
                    "%d mise(s) à jour, %d non trouvée(s)%n",
                    updated, notFoundRows.size()
            );
        }
        return 0;
    }

    private Map<String, Integer> resolveExternalIds(Set<String> externalIds) {
        Map<String, Integer> result = new HashMap<>();
        if (externalIds.isEmpty()) {
            return result;
        }
        Map<String, List<String>> byModule = new TreeMap<>();
        for (String xmlid : externalIds) {
            int dot = xmlid.indexOf('.');
            if (dot <= 0 || dot == xmlid.length() - 1) {
                System.err.println("External ID invalide ignoré : " + xmlid);
                continue;
            }
            String module = xmlid.substring(0, dot);
            String name = xmlid.substring(dot + 1);
            byModule.computeIfAbsent(module, k -> new ArrayList<>()).add(name);
        }
        for (Map.Entry<String, List<String>> entry : byModule.entrySet()) {
            String module = entry.getKey();
            JsonNode hits = odoo.searchRead(
                    "ir.model.data",
                    List.of(
                            List.of("module", "=", module),
                            List.of("name", "in", entry.getValue())
                    ),
                    List.of("id", "module", "name", "res_id")
            );
            if (hits != null && hits.isArray()) {
                for (JsonNode hit : hits) {
                    String key = hit.get("module").asText() + "." + hit.get("name").asText();
                    result.put(key, hit.get("res_id").asInt());
                }
            }
        }
        return result;
    }

    private Map<String, Integer> resolveBarcodeRules(Set<String> names) {
        Map<String, Integer> result = new HashMap<>();
        if (names.isEmpty()) {
            return result;
        }
        JsonNode hits = odoo.searchRead(
                "barcode.rule",
                List.of(List.of("name", "in", new ArrayList<>(names))),
                List.of("id", "name")
        );
        if (hits != null && hits.isArray()) {
            for (JsonNode hit : hits) {
                result.put(hit.get("name").asText(), hit.get("id").asInt());
            }
        }
        return result;
    }

    private static String formatChanges(Map<String, Object> values) {
        return values.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    private static List<Row> readCsv(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<Row> result = new ArrayList<>();
        boolean header = true;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = parseCsvLine(line);
            if (header) {
                header = false;
                continue;
            }
            String externalId = fields.length > 0 ? fields[0].trim() : "";
            String name = fields.length > 1 ? fields[1].trim() : "";
            String category = fields.length > 2 ? fields[2].trim() : "";
            String internalReference = fields.length > 3 ? fields[3].trim() : "";
            String barcodeRule = fields.length > 4 ? fields[4].trim() : "";
            String barcodeBase = fields.length > 5 ? fields[5].trim() : "";
            result.add(new Row(externalId, name, category, internalReference, barcodeRule, barcodeBase));
        }
        return result;
    }

    private static String[] parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else if (c == '"' && cur.length() == 0) {
                inQuotes = true;
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
