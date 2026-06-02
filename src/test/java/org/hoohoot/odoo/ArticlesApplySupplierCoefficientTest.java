package org.hoohoot.odoo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.hoohoot.odoo.test.WireMockOdooResource;
import org.hoohoot.odoo.test.WireMockOdooResource.Stub;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@QuarkusTestResource(WireMockOdooResource.class)
class ArticlesApplySupplierCoefficientTest {

    @Test
    void dryRunListsSlotsWithoutWriting(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.COEFFICIENT_FOUND,
                Stub.SUPPLIERINFO_FOR_COEFF,
                Stub.PRODUCT_TEMPLATE_COEFF_READ);

        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--supplier-id", "328",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02",
                "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("[dry-run]")
                .contains("Produit A").contains("coeff2")
                .contains("Produit B").contains("coeff3")
                .contains("Produit C").contains("déjà appliqué");
        assertThat(result.getErrorOutput()).contains("dry-run");
    }

    @Test
    void appliesToFirstFreeSlot(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.COEFFICIENT_FOUND,
                Stub.SUPPLIERINFO_FOR_COEFF,
                Stub.PRODUCT_TEMPLATE_COEFF_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--supplier-id", "328",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Produit A").contains("coeff2")
                .contains("Produit B").contains("coeff3")
                .contains("Produit C").contains("déjà appliqué");
        // Coefficient existant réutilisé, pas de création
        assertThat(result.getOutput()).doesNotContain("créé");
        assertThat(result.getErrorOutput()).contains("2 produit").contains("1 sauté");
    }

    @Test
    void createsCoefficientWhenMissing(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.COEFFICIENT_NOT_FOUND,
                Stub.COEFFICIENT_CREATE,
                Stub.SUPPLIERINFO_FOR_COEFF,
                Stub.PRODUCT_TEMPLATE_COEFF_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--supplier-id", "328",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("créé").contains("Surtaxe carburant 2026");
    }

    @Test
    void forcedSlotTargetsThatSlot(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.COEFFICIENT_FOUND,
                Stub.SUPPLIERINFO_FOR_COEFF,
                Stub.PRODUCT_TEMPLATE_COEFF_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--supplier-id", "328",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02",
                "--slot", "5");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Produit A").contains("coeff5");
        assertThat(result.getErrorOutput()).contains("2 produit");
    }

    @Test
    void forcedSlotSkipsWhenOccupied(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.COEFFICIENT_FOUND,
                Stub.SUPPLIERINFO_FOR_COEFF,
                Stub.PRODUCT_TEMPLATE_COEFF_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--supplier-id", "328",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02",
                "--slot", "2");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Produit A").contains("coeff2")
                .contains("Produit B").contains("slot 2 occupé");
    }

    @Test
    void resolvesSupplierByName(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.SUPPLIER_BY_NAME,
                Stub.COEFFICIENT_FOUND,
                Stub.SUPPLIERINFO_FOR_COEFF,
                Stub.PRODUCT_TEMPLATE_COEFF_READ,
                Stub.PRODUCT_TEMPLATE_WRITE);

        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--supplier-name", "ALVEUS GmbH",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("ALVEUS GmbH");
    }

    @Test
    void failsWhenSupplierNotFound(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(Stub.SUPPLIER_BY_NAME_NONE);

        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--supplier-name", "Inconnu",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02");

        assertThat(result.exitCode()).isEqualTo(2);
        assertThat(result.getErrorOutput()).contains("fournisseur");
    }

    @Test
    void failsWhenNoSupplierOption(QuarkusMainLauncher launcher) {
        LaunchResult result = launcher.launch(
                "articles", "apply-supplier-coefficient",
                "--name", "Surtaxe carburant 2026",
                "--value", "0.02");

        assertThat(result.exitCode()).isEqualTo(2);
    }
}
