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
class BarcodeRulesListTest {

    @BeforeEach
    void setupStubs() {
        WireMockOdooResource.expect(Stub.BARCODE_NOMENCLATURE_DEFAULT, Stub.BARCODE_RULES_LIST);
    }

    @Test
    @Launch({"barcode-rules", "list", "--output", "csv"})
    void listAllSortedBySequenceAsc(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Nom;Type;Encodage;Modèle;Date création;Séquence;Transformer")
                .contains("Meal Voucher Payment;meal_voucher_payment;any;...........{NNNDD}........;23/01/2026;1;")
                .contains("Customer Barcodes;client;any;042;23/01/2026;2;")
                .contains("Cashier Barcodes;cashier;ean13;041;23/01/2026;3;value * 2")
                .contains("Location barcodes;location;any;414;22/01/2026;4;value / 6.55957");

        int idxMeal = result.getOutput().indexOf("Meal Voucher Payment");
        int idxCustomer = result.getOutput().indexOf("Customer Barcodes");
        int idxCashier = result.getOutput().indexOf("Cashier Barcodes");
        int idxLocation = result.getOutput().indexOf("Location barcodes");
        assertThat(idxMeal).isLessThan(idxCustomer);
        assertThat(idxCustomer).isLessThan(idxCashier);
        assertThat(idxCashier).isLessThan(idxLocation);
        assertThat(result.getErrorOutput()).contains("4 règle(s)");
    }

    @Test
    @Launch({"barcode-rules", "list", "--output", "csv", "--sort-by", "nom"})
    void sortByName(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        int idxCashier = result.getOutput().indexOf("Cashier Barcodes");
        int idxCustomer = result.getOutput().indexOf("Customer Barcodes");
        int idxLocation = result.getOutput().indexOf("Location barcodes");
        int idxMeal = result.getOutput().indexOf("Meal Voucher Payment");
        assertThat(idxCashier).isLessThan(idxCustomer);
        assertThat(idxCustomer).isLessThan(idxLocation);
        assertThat(idxLocation).isLessThan(idxMeal);
    }

    @Test
    @Launch({"barcode-rules", "list", "--output", "csv", "--sort-by", "sequence", "--sort-direction", "desc"})
    void sortBySequenceDesc(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        int idxMeal = result.getOutput().indexOf("Meal Voucher Payment");
        int idxLocation = result.getOutput().indexOf("Location barcodes");
        assertThat(idxLocation).isLessThan(idxMeal);
    }

    @Test
    @Launch({"barcode-rules", "list", "--output", "csv", "--sort-by", "date"})
    void sortByCreateDate(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        int idxLocation = result.getOutput().indexOf("Location barcodes");
        int idxCashier = result.getOutput().indexOf("Cashier Barcodes");
        assertThat(idxLocation).isLessThan(idxCashier);
    }

    @Test
    @Launch({"barcode-rules", "list", "--output", "csv", "--sort-by", "encodage"})
    void sortByEncoding(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        int idxCashier = result.getOutput().indexOf("Cashier Barcodes");
        int idxMeal = result.getOutput().indexOf("Meal Voucher Payment");
        assertThat(idxMeal).isLessThan(idxCashier);
    }

    @Test
    @Launch({"barcode-rules", "list", "--output", "csv", "--sort-by", "transformer"})
    void sortByTransformer(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        int idxMeal = result.getOutput().indexOf("Meal Voucher Payment");
        int idxCustomer = result.getOutput().indexOf("Customer Barcodes");
        int idxCashier = result.getOutput().indexOf("Cashier Barcodes");
        int idxLocation = result.getOutput().indexOf("Location barcodes");
        assertThat(idxMeal).isLessThan(idxCashier);
        assertThat(idxCustomer).isLessThan(idxCashier);
        assertThat(idxCashier).isLessThan(idxLocation);
    }
}
