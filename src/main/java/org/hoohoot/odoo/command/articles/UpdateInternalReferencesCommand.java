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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "update-internal-references",
        description = "Met à jour en masse les références internes des produits depuis un CSV (en-tête: Name,InternalReference)",
        mixinStandardHelpOptions = true
)
public class UpdateInternalReferencesCommand implements Callable<Integer> {

    @Option(
            names = "--csv",
            required = true,
            paramLabel = "FILE",
            description = "Fichier CSV à 2 colonnes (en-tête: Name,InternalReference)"
    )
    Path csv;

    @Option(
            names = "--dry-run",
            description = "Simule l'exécution sans écrire dans Odoo"
    )
    boolean dryRun;

    @Inject
    OdooClient odoo;

    @Override
    public Integer call() {
        List<String[]> rows;
        try {
            rows = readCsv(csv);
        } catch (IOException e) {
            System.err.println("Erreur de lecture du CSV: " + e.getMessage());
            return 2;
        }

        int updated = 0;
        List<String[]> notFoundRows = new ArrayList<>();
        List<String[]> ambiguousRows = new ArrayList<>();

        for (String[] row : rows) {
            String name = row[0];
            String ref = row[1];

            JsonNode products = odoo.searchRead(
                    "product.product",
                    List.of(List.of("name", "=", name)),
                    List.of("id", "name", "default_code")
            );

            if (products == null || !products.isArray() || products.isEmpty()) {
                System.err.println("Non trouvé : " + name + " (cible: " + ref + ")");
                notFoundRows.add(row);
                continue;
            }
            if (products.size() > 1) {
                System.err.println("Ambigu (" + products.size() + " correspondances) : " + name + " (cible: " + ref + ")");
                ambiguousRows.add(row);
                continue;
            }

            int id = products.get(0).get("id").asInt();
            JsonNode currentCodeNode = products.get(0).get("default_code");
            String currentCode = currentCodeNode == null || currentCodeNode.isNull() || currentCodeNode.isBoolean()
                    ? ""
                    : currentCodeNode.asText();
            if (dryRun) {
                System.out.println("[dry-run] " + name + " : " + currentCode + " -> " + ref);
            } else {
                odoo.executeKw(
                        "product.product",
                        "write",
                        List.of(List.of(id), Map.of("default_code", ref))
                );
                System.out.println(name + " -> " + ref);
            }
            updated++;
        }

        if (!notFoundRows.isEmpty() || !ambiguousRows.isEmpty()) {
            System.err.println();
            System.err.println("=== Lignes non mises à jour ===");
            for (String[] row : notFoundRows) {
                System.err.println("  [non trouvé] " + row[0] + "," + row[1]);
            }
            for (String[] row : ambiguousRows) {
                System.err.println("  [ambigu]     " + row[0] + "," + row[1]);
            }
        }

        if (dryRun) {
            System.err.println("Mode dry-run : aucune modification effectuée");
            System.err.printf(
                    "%d mise(s) à jour simulée(s), %d non trouvée(s), %d ambigu(s)%n",
                    updated, notFoundRows.size(), ambiguousRows.size()
            );
        } else {
            System.err.printf(
                    "%d mise(s) à jour, %d non trouvée(s), %d ambigu(s)%n",
                    updated, notFoundRows.size(), ambiguousRows.size()
            );
        }
        return 0;
    }

    private static List<String[]> readCsv(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String[]> result = new ArrayList<>();
        boolean header = true;
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = parseCsvLine(line);
            if (fields.length < 2) {
                continue;
            }
            if (header) {
                header = false;
                continue;
            }
            result.add(new String[]{fields[0].trim(), fields[1].trim()});
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
