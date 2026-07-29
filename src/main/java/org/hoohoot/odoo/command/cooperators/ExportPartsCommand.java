package org.hoohoot.odoo.command.cooperators;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.OdooClient;
import org.hoohoot.odoo.format.CsvFormatter;
import org.hoohoot.odoo.format.PrettyFormatter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "export-parts",
        description = "Exporte les parts sociales détenues par coopérateur",
        mixinStandardHelpOptions = true
)
public class ExportPartsCommand implements Callable<Integer> {

    public enum OutputFormat { pretty, csv }

    @Option(
            names = "--category",
            paramLabel = "NOM",
            description = "Catégorie de parts à exporter (défaut: ${DEFAULT-VALUE})",
            defaultValue = "Parts A"
    )
    String category;

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

    private record Holding(String nom, long parts, double montant) {
    }

    @Override
    public Integer call() {
        JsonNode categories = odoo.searchRead(
                "capital.fundraising.category",
                List.of(List.of("name", "=", category)),
                List.of("id", "product_id")
        );
        if (categories == null || !categories.isArray() || categories.isEmpty()) {
            System.err.println("Catégorie de parts introuvable : " + category);
            return 1;
        }
        JsonNode categoryNode = categories.get(0);
        int categoryId = categoryNode.get("id").asInt();
        JsonNode productRef = categoryNode.get("product_id");
        if (productRef == null || !productRef.isArray() || productRef.isEmpty()) {
            System.err.println("Pas de produit associé à la catégorie : " + category);
            return 1;
        }
        int productId = productRef.get(0).asInt();

        JsonNode products = odoo.searchRead(
                "product.product",
                List.of(
                        List.of("id", "=", productId),
                        List.of("active", "in", List.of(true, false))
                ),
                List.of("list_price")
        );
        if (products == null || !products.isArray() || products.isEmpty()) {
            System.err.println("Produit introuvable pour la catégorie : " + category);
            return 1;
        }
        double listPrice = products.get(0).path("list_price").asDouble(0);

        JsonNode shares = odoo.searchRead(
                "res.partner.owned.share",
                List.of(
                        List.of("category_id", "=", categoryId),
                        List.of("owned_share", ">", 0)
                ),
                List.of("partner_id", "owned_share")
        );

        Map<Integer, Long> sharesByPartner = new HashMap<>();
        Map<Integer, String> nameByPartner = new HashMap<>();
        if (shares != null && shares.isArray()) {
            for (JsonNode line : shares) {
                JsonNode partnerRef = line.get("partner_id");
                if (partnerRef == null || !partnerRef.isArray() || partnerRef.isEmpty()) {
                    continue;
                }
                int partnerId = partnerRef.get(0).asInt();
                sharesByPartner.merge(partnerId, line.path("owned_share").asLong(0), Long::sum);
                nameByPartner.putIfAbsent(partnerId, stripMemberNumber(partnerRef.get(1).asText("")));
            }
        }

        List<Holding> holdings = new ArrayList<>();
        for (Map.Entry<Integer, Long> entry : sharesByPartner.entrySet()) {
            long parts = entry.getValue();
            holdings.add(new Holding(nameByPartner.get(entry.getKey()), parts, parts * listPrice));
        }
        holdings.sort(Comparator.comparing(Holding::nom, String.CASE_INSENSITIVE_ORDER));

        String[] headers = {"Nom", "Nombre de parts", "Montant total"};
        String[][] rows = toRows(holdings);
        switch (output) {
            case csv -> csv.print(headers, rows);
            case pretty -> pretty.print(headers, rows, new boolean[]{false, true, true});
        }

        long totalParts = 0;
        double totalMontant = 0;
        for (Holding h : holdings) {
            totalParts += h.parts();
            totalMontant += h.montant();
        }
        System.err.printf("%d détenteur(s) de %s — %d parts — montant total %s%n",
                holdings.size(), category, totalParts, formatAmount(totalMontant));
        return 0;
    }

    private static String[][] toRows(List<Holding> holdings) {
        String[][] rows = new String[holdings.size()][];
        for (int i = 0; i < holdings.size(); i++) {
            Holding h = holdings.get(i);
            rows[i] = new String[]{h.nom(), String.valueOf(h.parts()), formatAmount(h.montant())};
        }
        return rows;
    }

    private static String stripMemberNumber(String displayName) {
        return displayName.replaceFirst("^\\d+\\s*-\\s*", "");
    }

    private static String formatAmount(double amount) {
        if (amount == Math.floor(amount)) {
            return String.valueOf((long) amount);
        }
        return String.valueOf(amount);
    }
}
