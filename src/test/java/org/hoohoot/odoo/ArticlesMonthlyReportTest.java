package org.hoohoot.odoo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.hoohoot.odoo.test.WireMockOdooResource;
import org.hoohoot.odoo.test.WireMockOdooResource.Stub;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@QuarkusTestResource(WireMockOdooResource.class)
class ArticlesMonthlyReportTest {

    @Test
    void sendsReportByEmail(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.PRODUCTS_CREATED_SINCE,
                Stub.PRODUCTS_ARCHIVED_SINCE,
                Stub.POS_ORDER_LINE_QTY,
                Stub.BREVO_EMAIL_SENT);

        LaunchResult result = launcher.launch("articles", "monthly-report",
                "--month", "2026-05",
                "--sender", "rapport@hoohoot.org",
                "--recipients", "a@example.com,b@example.com");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput())
                .contains("envoyé")
                .contains("a@example.com, b@example.com")
                .contains("2 créé(s)")
                .contains("2 archivé(s)");
    }

    @Test
    void dryRunWritesXlsxWithSoldQuantities(QuarkusMainLauncher launcher) throws Exception {
        // pas de stub Brevo : un envoi réel échouerait
        WireMockOdooResource.expect(
                Stub.PRODUCTS_CREATED_SINCE, Stub.PRODUCTS_ARCHIVED_SINCE, Stub.POS_ORDER_LINE_QTY);

        Path out = Path.of("produits-2026-05.xlsx");
        Files.deleteIfExists(out);
        try {
            LaunchResult result = launcher.launch("articles", "monthly-report",
                    "--month", "2026-05",
                    "--sender", "rapport@hoohoot.org",
                    "--recipients", "a@example.com",
                    "--dry-run");

            assertThat(result.exitCode()).isZero();
            assertThat(result.getOutput())
                    .contains("[dry-run]")
                    .contains("2 produit(s) créé(s)")
                    .contains("2 archivé(s)");
            assertThat(Files.exists(out)).isTrue();

            byte[] bytes = Files.readAllBytes(out);
            // feuille 1 : produits créés + quantités vendues
            assertThat(readSheet(bytes, "xl/worksheets/sheet1.xml"))
                    .contains("Produit Créé A")
                    .contains("Produit Créé B")
                    .contains("<v>125.219</v>")
                    .contains("<v>39.0</v>");
            // feuille 2 : produits archivés
            assertThat(readSheet(bytes, "xl/worksheets/sheet2.xml"))
                    .contains("Archivé le")
                    .contains("Produit Archivé A")
                    .contains("Produit Archivé B");
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void rejectsInvalidMonthFormat(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("articles", "monthly-report",
                "--month", "05-2026",
                "--sender", "rapport@hoohoot.org",
                "--recipients", "a@example.com");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.getErrorOutput()).contains("mauvais format de mois");
    }

    @Test
    void requiresMandatoryOptions(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch("articles", "monthly-report");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.getErrorOutput())
                .contains("--sender")
                .contains("--recipients");
    }

    private static String readSheet(byte[] bytes, String entryName) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    zip.transferTo(o);
                    return o.toString(StandardCharsets.UTF_8);
                }
            }
        }
        return "";
    }
}
