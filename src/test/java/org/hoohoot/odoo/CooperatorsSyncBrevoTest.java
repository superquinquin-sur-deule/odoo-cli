package org.hoohoot.odoo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.hoohoot.odoo.test.WireMockOdooResource;
import org.hoohoot.odoo.test.WireMockOdooResource.Stub;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@QuarkusTestResource(WireMockOdooResource.class)
class CooperatorsSyncBrevoTest {

    @Test
    void syncsNewCooperatorsToBrevoList(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(Stub.MEMBERS_SINCE, Stub.BREVO_CONTACT_CREATED);

        LaunchResult result = launcher.launch("cooperators", "sync-brevo",
                "--since", "06/06/2026", "--brevo-list-id", "42");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("GRAS, Pierre-Louis <plmp.gras@wanadoo.fr>")
                .contains("FLORET, Faustine <faust.floret@gmail.com>")
                .contains("Madonna <madonna@example.com>")
                .contains("ajouté");
        assertThat(result.getErrorOutput()).contains("3 contact(s) ajouté(s)");
    }

    @Test
    void skipsContactsAlreadyInBrevo(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.MEMBERS_SINCE,
                Stub.BREVO_CONTACT_CREATED,
                Stub.BREVO_CONTACT_DUPLICATE);

        LaunchResult result = launcher.launch("cooperators", "sync-brevo",
                "--since", "06/06/2026", "--brevo-list-id", "42");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("FLORET, Faustine <faust.floret@gmail.com> : déjà présent dans Brevo, ignoré");
        assertThat(result.getErrorOutput())
                .contains("2 contact(s) ajouté(s)")
                .contains("1 ignoré(s)");
    }

    @Test
    void dryRunDoesNotCallBrevo(QuarkusMainLauncher launcher) {
        // aucun stub Brevo : un POST /v3/contacts renverrait 404 → erreur → exit code != 0
        WireMockOdooResource.expect(Stub.MEMBERS_SINCE);

        LaunchResult result = launcher.launch("cooperators", "sync-brevo",
                "--since", "06/06/2026", "--brevo-list-id", "42", "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("[dry-run]")
                .contains("GRAS, Pierre-Louis <plmp.gras@wanadoo.fr>")
                .contains("Madonna <madonna@example.com>");
        assertThat(result.getErrorOutput()).contains("dry-run").contains("3 contact(s)");
    }

    @Test
    @Launch(value = {"cooperators", "sync-brevo", "--since", "31-12-2026", "--brevo-list-id", "42"}, exitCode = 2)
    void rejectsInvalidDateFormat(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("mauvais format de date");
    }

    @Test
    @Launch(value = {"cooperators", "sync-brevo"}, exitCode = 2)
    void requiresMandatoryOptions(LaunchResult result) {
        assertThat(result.getErrorOutput())
                .contains("--since")
                .contains("--brevo-list-id");
    }
}
