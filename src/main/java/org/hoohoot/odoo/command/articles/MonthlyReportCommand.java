package org.hoohoot.odoo.command.articles;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.inject.Inject;
import org.hoohoot.odoo.client.BrevoClient;
import org.hoohoot.odoo.client.OdooClient;
import org.hoohoot.odoo.format.XlsxWriter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "monthly-report",
        description = "Génère un rapport mensuel des produits créés sur la période (nom, date de "
                + "création, créateur, quantité vendue en POS) et l'envoie en pièce jointe xlsx par email via Brevo",
        mixinStandardHelpOptions = true
)
public class MonthlyReportCommand implements Callable<Integer> {

    private static final Locale FR = Locale.FRENCH;
    private static final DateTimeFormatter ODOO_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter MONTH_INPUT = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final DateTimeFormatter DATE_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Option(
            names = "--sender",
            paramLabel = "EMAIL",
            required = true,
            description = "Adresse email de l'émetteur (doit être un expéditeur validé dans Brevo)"
    )
    String sender;

    @Option(
            names = "--sender-name",
            paramLabel = "NOM",
            defaultValue = "Rapport produits",
            description = "Nom affiché de l'émetteur (défaut : ${DEFAULT-VALUE})"
    )
    String senderName;

    @Option(
            names = "--recipients",
            paramLabel = "EMAILS",
            required = true,
            split = ",",
            description = "Liste des destinataires séparés par des virgules"
    )
    List<String> recipients;

    @Option(
            names = "--month",
            paramLabel = "AAAA-MM",
            description = "Mois à couvrir (ex. 2026-05). Par défaut : mois courant jusqu'à aujourd'hui"
    )
    String month;

    @Option(
            names = "--dry-run",
            description = "Génère le xlsx sur disque et affiche le résumé, sans envoyer l'email"
    )
    boolean dryRun;

    @Inject
    OdooClient odoo;

    @Inject
    BrevoClient brevo;

    @Inject
    XlsxWriter xlsx;

    @Override
    public Integer call() {
        YearMonth period;
        if (month != null && !month.isBlank()) {
            try {
                period = YearMonth.parse(month, MONTH_INPUT);
            } catch (Exception e) {
                System.err.println(month + " : mauvais format de mois (AAAA-MM) pour --month");
                return 2;
            }
        } else {
            period = YearMonth.now();
        }

        LocalDate start = period.atDay(1);
        LocalDate end = period.plusMonths(1).atDay(1);

        // 1. Produits créés sur la période
        List<Object> domain = List.of(
                List.of("create_date", ">=", start.atStartOfDay().format(ODOO_DATETIME)),
                List.of("create_date", "<", end.atStartOfDay().format(ODOO_DATETIME))
        );
        JsonNode products = odoo.searchRead("product.template", domain,
                List.of("name", "create_date", "create_uid", "product_variant_ids"));

        List<Product> rows = new ArrayList<>();
        Map<Integer, Product> byVariant = new HashMap<>();
        if (products != null && products.isArray()) {
            for (JsonNode p : products) {
                Product product = new Product(
                        p.path("name").asText(""),
                        formatDate(p.path("create_date").asText("")),
                        p.path("create_uid").isArray() ? p.path("create_uid").get(1).asText("") : "");
                rows.add(product);
                for (JsonNode variantId : p.path("product_variant_ids")) {
                    byVariant.put(variantId.asInt(), product);
                }
            }
        }

        // 2. Quantités vendues en POS depuis la création (= toutes les ventes du produit)
        if (!byVariant.isEmpty()) {
            List<Object> qtyDomain = List.of(List.of("product_id", "in", new ArrayList<>(byVariant.keySet())));
            JsonNode groups = odoo.executeKw("pos.order.line", "read_group",
                    List.of(qtyDomain, List.of("qty"), List.of("product_id")));
            if (groups != null && groups.isArray()) {
                for (JsonNode group : groups) {
                    JsonNode productId = group.path("product_id");
                    if (productId.isArray()) {
                        Product product = byVariant.get(productId.get(0).asInt());
                        if (product != null) {
                            product.qtySold += group.path("qty").asDouble(0);
                        }
                    }
                }
            }
        }

        // 3. Produits archivés sur la période (active=false : Odoo désactive le filtre actif par
        // défaut dès que le domaine cite 'active'). Pas de date d'archivage native : on utilise
        // write_date comme proxy (dernière modification d'un produit désormais inactif).
        List<Object> archivedDomain = List.of(
                List.of("active", "=", false),
                List.of("write_date", ">=", start.atStartOfDay().format(ODOO_DATETIME)),
                List.of("write_date", "<", end.atStartOfDay().format(ODOO_DATETIME))
        );
        JsonNode archivedNode = odoo.searchRead("product.template", archivedDomain,
                List.of("name", "create_date", "write_date", "write_uid"));

        List<Object[]> archivedRows = new ArrayList<>();
        if (archivedNode != null && archivedNode.isArray()) {
            for (JsonNode p : archivedNode) {
                archivedRows.add(new Object[]{
                        p.path("name").asText(""),
                        formatDate(p.path("create_date").asText("")),
                        formatDate(p.path("write_date").asText("")),
                        p.path("write_uid").isArray() ? p.path("write_uid").get(1).asText("") : ""
                });
            }
        }

        // 4. Génération du xlsx (une feuille pour les créations, une pour les archivages)
        List<Object[]> createdRows = new ArrayList<>();
        for (Product product : rows) {
            createdRows.add(new Object[]{product.name, product.createdOn, product.createdBy, product.qtySold});
        }
        byte[] file = xlsx.build(List.of(
                new XlsxWriter.Sheet("Produits créés",
                        new String[]{"Nom du produit", "Créé le", "Créé par", "Quantité vendue"}, createdRows),
                new XlsxWriter.Sheet("Produits archivés",
                        new String[]{"Nom du produit", "Créé le", "Archivé le", "Archivé par"}, archivedRows)
        ));

        String periodLabel = period.getMonth().getDisplayName(TextStyle.FULL, FR) + " " + period.getYear();
        String fileName = "produits-" + period.format(MONTH_INPUT) + ".xlsx";

        // 5. Envoi (ou écriture locale en dry-run)
        if (dryRun) {
            try {
                Path out = Path.of(fileName);
                Files.write(out, file);
                System.out.printf("[dry-run] %d produit(s) créé(s), %d archivé(s) — fichier écrit : %s%n",
                        rows.size(), archivedRows.size(), out.toAbsolutePath());
            } catch (IOException e) {
                System.err.println("Échec d'écriture du fichier : " + e.getMessage());
                return 1;
            }
            System.err.printf("Mode dry-run : aucun email envoyé (%s, %d créé(s), %d archivé(s))%n",
                    periodLabel, rows.size(), archivedRows.size());
            return 0;
        }

        String subject = "Rapport mensuel — produits (" + periodLabel + ")";
        String html = "<p>Bonjour,</p><p>Tu trouveras ci-joint le rapport des produits pour "
                + periodLabel + " : " + rows.size() + " produit(s) créé(s) (avec leur quantité vendue "
                + "en magasin) et " + archivedRows.size() + " produit(s) archivé(s).</p>";

        BrevoClient.SendResult result = brevo.sendTransactionalEmail(
                sender, senderName, recipients, subject, html, file, fileName);

        if (result.success()) {
            System.err.printf("Rapport %s (%d créé(s), %d archivé(s)) envoyé à %s%n",
                    periodLabel, rows.size(), archivedRows.size(), String.join(", ", recipients));
            return 0;
        }
        System.err.println("Erreur Brevo lors de l'envoi : " + result.message());
        return 1;
    }

    private static String formatDate(String odooDatetime) {
        if (odooDatetime == null || odooDatetime.length() < 10) {
            return "";
        }
        try {
            return LocalDate.parse(odooDatetime.substring(0, 10)).format(DATE_DISPLAY);
        } catch (Exception e) {
            return odooDatetime;
        }
    }

    private static final class Product {
        final String name;
        final String createdOn;
        final String createdBy;
        double qtySold;

        Product(String name, String createdOn, String createdBy) {
            this.name = name;
            this.createdOn = createdOn;
            this.createdBy = createdBy;
        }
    }
}
