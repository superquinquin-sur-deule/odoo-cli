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
class CooperatorsFixBinomeEmailsTest {

    @Test
    void dryRunSimulates(QuarkusMainLauncher launcher) {
        // pas de stub d'écriture : si la commande écrivait, l'appel échouerait
        WireMockOdooResource.expect(
                Stub.BINOME_CONTACTS_MISSING_EMAIL,
                Stub.BINOME_EMAIL_SOURCES);

        LaunchResult result = launcher.launch("cooperators", "fix-binome-emails", "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("[dry-run]")
                .contains("RENARD, Fanny")
                .contains("fanny_renard@outlook.com");
        // ignorés listés : sans source (NOMATCH) et homonymes ambigus (AMBIGU)
        assertThat(result.getErrorOutput())
                .contains("dry-run")
                .contains("NOMATCH, Person")
                .contains("AMBIGU, Two")
                .contains("1 email")
                .contains("1 sans source")
                .contains("1 ambigu");
    }

    @Test
    void writesByDefault(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.BINOME_CONTACTS_MISSING_EMAIL,
                Stub.BINOME_EMAIL_SOURCES,
                Stub.PARTNER_EMAIL_WRITE);

        LaunchResult result = launcher.launch("cooperators", "fix-binome-emails");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .doesNotContain("[dry-run]")
                .contains("RENARD, Fanny")
                .contains("fanny_renard@outlook.com");
        assertThat(result.getErrorOutput())
                .contains("1 email")
                .contains("1 sans source")
                .contains("1 ambigu");
    }

    @Test
    void reportsNothingWhenNoBinome(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(Stub.BINOME_NO_MISSING_EMAIL);

        LaunchResult result = launcher.launch("cooperators", "fix-binome-emails");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput()).contains("rien à faire");
    }
}
