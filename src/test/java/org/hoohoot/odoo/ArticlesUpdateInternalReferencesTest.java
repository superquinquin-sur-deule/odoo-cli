package org.hoohoot.odoo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.hoohoot.odoo.test.WireMockOdooResource;
import org.hoohoot.odoo.test.WireMockOdooResource.Stub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@QuarkusTestResource(WireMockOdooResource.class)
class ArticlesUpdateInternalReferencesTest {

    private static final String CSV_HEADER =
            "ExternalId,Name,Category,InternalReference,BarcodeRule,BarcodeBase\n";

    @Test
    void updatesAllFieldsForRowWithExternalId(QuarkusMainLauncher launcher, @TempDir Path tmp) throws Exception {
        WireMockOdooResource.expect(
                Stub.IR_MODEL_DATA_LOOKUP,
                Stub.BARCODE_RULE_LOOKUP,
                Stub.PRODUCT_TEMPLATE_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        Path csv = tmp.resolve("products.csv");
        Files.writeString(csv, CSV_HEADER
                + "__export__.product_template_3802,Ail sec BIO,__export__.product_category_146,500,"
                + "Price Look Up Codes (PLU Codes),500\n");

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Ail sec BIO")
                .contains("default_code=500")
                .contains("categ_id=201")
                .contains("barcode_rule_id=118")
                .contains("barcode_base=500");
        assertThat(result.getErrorOutput()).contains("1 mise(s) à jour");
        assertThat(result.getErrorOutput()).contains("0 non trouvé");
    }

    @Test
    void updatesOnlyProvidedColumns(QuarkusMainLauncher launcher, @TempDir Path tmp) throws Exception {
        WireMockOdooResource.expect(
                Stub.IR_MODEL_DATA_LOOKUP,
                Stub.PRODUCT_TEMPLATE_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        Path csv = tmp.resolve("products.csv");
        Files.writeString(csv, CSV_HEADER
                + "__export__.product_template_34039_7d5bebe8,Aillet botte BIO,__export__.product_category_146,,,\n");

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Aillet botte BIO")
                .contains("categ_id=201")
                .doesNotContain("default_code=")
                .doesNotContain("barcode_rule_id=")
                .doesNotContain("barcode_base=");
        assertThat(result.getErrorOutput()).contains("1 mise(s) à jour");
    }

    @Test
    void skipsRowsWithUnknownExternalId(QuarkusMainLauncher launcher, @TempDir Path tmp) throws Exception {
        WireMockOdooResource.expect(
                Stub.IR_MODEL_DATA_LOOKUP,
                Stub.BARCODE_RULE_LOOKUP,
                Stub.PRODUCT_TEMPLATE_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        Path csv = tmp.resolve("products.csv");
        Files.writeString(csv, CSV_HEADER
                + "__export__.product_template_3802,Ail sec BIO,__export__.product_category_146,500,Price Look Up Codes (PLU Codes),500\n"
                + "__export__.product_template_99999,Inconnu,__export__.product_category_146,777,Price Look Up Codes (PLU Codes),777\n");

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput())
                .contains("1 mise(s) à jour")
                .contains("1 non trouvé")
                .contains("__export__.product_template_99999");
    }

    @Test
    void dryRunDoesNotWriteAndReportsSimulation(QuarkusMainLauncher launcher, @TempDir Path tmp) throws Exception {
        WireMockOdooResource.expect(
                Stub.IR_MODEL_DATA_LOOKUP,
                Stub.BARCODE_RULE_LOOKUP,
                Stub.PRODUCT_TEMPLATE_READ);

        Path csv = tmp.resolve("products.csv");
        Files.writeString(csv, CSV_HEADER
                + "__export__.product_template_3802,Ail sec BIO,__export__.product_category_146,500,Price Look Up Codes (PLU Codes),500\n");

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString(), "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("[dry-run] Ail sec BIO")
                .contains("default_code=500")
                .contains("categ_id=201")
                .contains("barcode_rule_id=118")
                .contains("barcode_base=500");
        assertThat(result.getErrorOutput())
                .contains("Mode dry-run : aucune modification effectuée")
                .contains("1 mise(s) à jour simulée(s)");
    }

    @Test
    void failsWhenCsvMissing(QuarkusMainLauncher launcher, @TempDir Path tmp) {
        WireMockOdooResource.expect();

        Path csv = tmp.resolve("missing.csv");

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString());

        assertThat(result.exitCode()).isNotZero();
    }
}
