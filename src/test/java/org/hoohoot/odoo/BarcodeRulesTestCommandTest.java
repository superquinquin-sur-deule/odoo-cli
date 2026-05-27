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
class BarcodeRulesTestCommandTest {

    @BeforeEach
    void setupStubs() {
        WireMockOdooResource.expect(Stub.BARCODE_NOMENCLATURE_DEFAULT, Stub.BARCODE_RULES_LIST);
    }

    @Test
    @Launch({"barcode-rules", "test", "04212345", "--output", "csv"})
    void matchesByPrefix(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Séq.;Nom;Type;Encodage;Modèle;Match;Valeur")
                .contains("1;Meal Voucher Payment;meal_voucher_payment;any;...........{NNNDD}........;✗")
                .contains("2;Customer Barcodes;client;any;042;→")
                .contains("3;Cashier Barcodes;cashier;ean13;041;✗")
                .contains("4;Location barcodes;location;any;414;✗");
        assertThat(result.getErrorOutput())
                .contains("Règle appliquée : Customer Barcodes")
                .contains("séquence 2")
                .contains("type=client");
    }

    @Test
    @Launch({"barcode-rules", "test", "99999999", "--output", "csv"})
    void noMatch(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("1;Meal Voucher Payment;meal_voucher_payment;any;...........{NNNDD}........;✗")
                .contains("2;Customer Barcodes;client;any;042;✗")
                .contains("4;Location barcodes;location;any;414;✗");
        assertThat(result.getErrorOutput())
                .contains("Aucune règle applicable");
    }

    @Test
    @Launch({"barcode-rules", "test", "abcdefghijk12345lmnopqrs", "--output", "csv"})
    void extractsNumericValue(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("1;Meal Voucher Payment;meal_voucher_payment;any;...........{NNNDD}........;→;123.45");
        assertThat(result.getErrorOutput())
                .contains("Meal Voucher Payment")
                .contains("123.45");
    }

    @Test
    @Launch({"barcode-rules", "test", "414something"})
    void prettyOutputShowsAppliedRule(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Location barcodes")
                .contains("→");
        assertThat(result.getErrorOutput())
                .contains("Règle appliquée : Location barcodes");
    }
}
