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
class CooperatorsListTest {

    @BeforeEach
    void setupStubs() {
        WireMockOdooResource.expect(Stub.PARTNERS, Stub.BINOMES, Stub.INVOICES);
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv"})
    void listAll(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Doe;Alice;alice@example.com")
                .contains("Smith;Bob;bob@example.com")
                .contains("Jones;Carol;alice@example.com")
                .contains("Brown;Dave");
        assertThat(result.getErrorOutput()).contains("4 coopérateur");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv"})
    void statusInOutput(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Statut")
                .contains("Doe;Alice;alice@example.com;1 rue 75001 Paris;1;100;15/01/2020;up_to_date")
                .contains("Smith;Bob;bob@example.com;;1;50;10/06/2021;alert")
                .contains("Jones;Carol;alice@example.com;;1;75;20/03/2019;unsubscribed")
                .contains("Brown;Dave;;;1;25;05/11/2022;up_to_date");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--status", "up_to_date"})
    void filterByStatusSingle(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Doe;Alice")
                .contains("Brown;Dave")
                .doesNotContain("Smith;Bob")
                .doesNotContain("Jones;Carol");
        assertThat(result.getErrorOutput()).contains("2 coopérateur");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--status", "alert", "--status", "unsubscribed"})
    void filterByStatusMultiple(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Smith;Bob")
                .contains("Jones;Carol")
                .doesNotContain("Doe;Alice")
                .doesNotContain("Brown;Dave");
        assertThat(result.getErrorOutput()).contains("2 coopérateur");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--exclude-binomes"})
    void excludeBinomes(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Doe;Alice")
                .contains("Smith;Bob")
                .contains("Jones;Carol")
                .doesNotContain("Brown;Dave");
        assertThat(result.getErrorOutput()).contains("3 coopérateur");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--sort-by", "statut"})
    void sortByStatusAsc(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        String output = result.getOutput();
        int smith = output.indexOf("Smith;Bob");
        int jones = output.indexOf("Jones;Carol");
        int doe = output.indexOf("Doe;Alice");
        int brown = output.indexOf("Brown;Dave");
        assertThat(smith).isLessThan(jones);
        assertThat(jones).isLessThan(doe);
        assertThat(doe).isLessThan(brown);
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--no-email"})
    void noEmail(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Brown;Dave")
                .doesNotContain("alice@example.com")
                .doesNotContain("bob@example.com");
        assertThat(result.getErrorOutput()).contains("1 coopérateur");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--duplicate-email"})
    void duplicateEmail(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Doe;Alice;alice@example.com")
                .contains("Jones;Carol;alice@example.com")
                .doesNotContain("Smith;Bob")
                .doesNotContain("Brown;Dave");
        assertThat(result.getErrorOutput()).contains("2 coopérateur");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--group-by", "binome"})
    void groupByBinomeCsv(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        String output = result.getOutput();
        assertThat(output)
                .contains("Smith;Bob;bob@example.com")
                .contains("└─→;Smith;Eve;eve@example.com");
        int bobIdx = output.indexOf("Smith;Bob");
        int eveIdx = output.indexOf("Smith;Eve");
        assertThat(eveIdx).isGreaterThan(bobIdx);
    }

    @Test
    @Launch({"cooperators", "list", "--group-by", "binome"})
    void groupByBinomePretty(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        String output = result.getOutput();
        assertThat(output)
                .contains("Bob")
                .contains("└─→")
                .contains("Eve");
        int bobIdx = output.indexOf("Bob");
        int eveIdx = output.indexOf("Eve");
        int arrowIdx = output.indexOf("└─→");
        assertThat(arrowIdx).isGreaterThan(bobIdx);
        assertThat(eveIdx).isGreaterThan(arrowIdx);
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv"})
    void inscriptionDateInOutput(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Inscription")
                .contains("Doe;Alice;alice@example.com;1 rue 75001 Paris;1;100;15/01/2020")
                .contains("Smith;Bob;bob@example.com;;1;50;10/06/2021")
                .contains("Jones;Carol;alice@example.com;;1;75;20/03/2019")
                .contains("Brown;Dave;;;1;25;05/11/2022");
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--sort-by", "capital"})
    void sortByCapitalAsc(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        String output = result.getOutput();
        int brown = output.indexOf("Brown;Dave");
        int smith = output.indexOf("Smith;Bob");
        int jones = output.indexOf("Jones;Carol");
        int doe = output.indexOf("Doe;Alice");
        assertThat(brown).isLessThan(smith);
        assertThat(smith).isLessThan(jones);
        assertThat(jones).isLessThan(doe);
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--sort-by", "capital", "--sort-direction", "desc"})
    void sortByCapitalDesc(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        String output = result.getOutput();
        int brown = output.indexOf("Brown;Dave");
        int smith = output.indexOf("Smith;Bob");
        int jones = output.indexOf("Jones;Carol");
        int doe = output.indexOf("Doe;Alice");
        assertThat(doe).isLessThan(jones);
        assertThat(jones).isLessThan(smith);
        assertThat(smith).isLessThan(brown);
    }

    @Test
    @Launch({"cooperators", "list", "--output", "csv", "--sort-by", "inscription"})
    void sortByInscriptionAsc(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        String output = result.getOutput();
        int jones = output.indexOf("Jones;Carol");
        int doe = output.indexOf("Doe;Alice");
        int smith = output.indexOf("Smith;Bob");
        int brown = output.indexOf("Brown;Dave");
        assertThat(jones).isLessThan(doe);
        assertThat(doe).isLessThan(smith);
        assertThat(smith).isLessThan(brown);
    }
}
