package org.hoohoot.odoo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.hoohoot.odoo.test.WireMockOdooResource;
import org.hoohoot.odoo.test.WireMockOdooResource.Stub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@QuarkusTestResource(WireMockOdooResource.class)
class CooperatorsExportPartsTest {

    @BeforeEach
    void setupStubs() {
        WireMockOdooResource.expect(Stub.CAPITAL_CATEGORY_PARTS_A, Stub.PARTS_PRODUCT_PRICE, Stub.OWNED_SHARES_PARTS_A);
    }

    @Test
    @Launch({"cooperators", "export-parts", "--output", "csv"})
    void exportCsv(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        String out = result.getOutput();
        assertThat(out)
                .contains("Nom;Nombre de parts;Montant total")
                .contains("AHRAS, Cemile;10;100")
                .contains("ZULU, Anna;5;50")
                .contains("BERGER, Léo;1;10")
                .doesNotContain("203 - ");
        assertThat(out.indexOf("AHRAS")).isLessThan(out.indexOf("BERGER"));
        assertThat(out.indexOf("BERGER")).isLessThan(out.indexOf("ZULU"));
        assertThat(result.getErrorOutput()).contains("3 détenteur(s) de Parts A");
    }

    @Test
    @Launch({"cooperators", "export-parts"})
    void exportPretty(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Nom")
                .contains("Nombre de parts")
                .contains("Montant total")
                .contains("AHRAS, Cemile")
                .contains("100");
        assertThat(result.getErrorOutput()).contains("16 parts").contains("montant total 160");
    }
}
