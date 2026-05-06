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
        int notFound = 0;
        int ambiguous = 0;

        for (String[] row : rows) {
            String name = row[0];
            String ref = row[1];

            JsonNode products = odoo.searchRead(
                    "product.product",
                    List.of(List.of("name", "=", name)),
                    List.of("id", "name", "default_code")
            );

            if (products == null || !products.isArray() || products.isEmpty()) {
                System.err.println("Non trouvé : " + name);
                notFound++;
                continue;
            }
            if (products.size() > 1) {
                System.err.println("Ambigu (" + products.size() + " correspondances) : " + name);
                ambiguous++;
                continue;
            }

            int id = products.get(0).get("id").asInt();
            odoo.executeKw(
                    "product.product",
                    "write",
                    List.of(List.of(id), Map.of("default_code", ref))
            );
            System.out.println(name + " -> " + ref);
            updated++;
        }

        System.err.printf(
                "%d mise(s) à jour, %d non trouvée(s), %d ambigu(s)%n",
                updated, notFound, ambiguous
        );
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
