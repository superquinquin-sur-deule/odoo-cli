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
class CooperatorsResetFtopCounterTest {

    @Test
    void resetsAllFtopCounterEvents(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.FTOP_COUNTER_EVENTS_READ,
                Stub.SHIFT_COUNTER_EVENT_WRITE);

        LaunchResult result = launcher.launch("cooperators", "reset-ftop-counter");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("ARDOUIN PERO, Joëlle")
                .contains("BELVA, Frederic")
                .contains("LONGUEVAL, Bertrande");
        assertThat(result.getErrorOutput()).contains("3 event").contains("3 coopérateur");
    }

    @Test
    void dryRunDoesNotWrite(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(Stub.FTOP_COUNTER_EVENTS_READ);

        LaunchResult result = launcher.launch("cooperators", "reset-ftop-counter", "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("[dry-run]");
        assertThat(result.getErrorOutput()).contains("dry-run").contains("simulé");
    }

    @Test
    void reportsZeroWhenNoEvents(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(Stub.NO_COUNTER_EVENTS);

        LaunchResult result = launcher.launch("cooperators", "reset-ftop-counter");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput()).contains("0 event");
    }

    @Test
    @Launch(value = {"cooperators", "reset-ftop-counter", "--before-date", "31-12-2026"}, exitCode = 2)
    void rejectsInvalidDateFormat(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("mauvais format de date");
    }

    @Test
    void partnerIdFilterRestrictsDomain(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.FTOP_COUNTER_EVENTS_PARTNER_FILTER,
                Stub.SHIFT_COUNTER_EVENT_WRITE);

        LaunchResult result = launcher.launch(
                "cooperators", "reset-ftop-counter",
                "--partner-id", "1689", "--partner-id", "1879");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("ARDOUIN PERO, Joëlle")
                .contains("LONGUEVAL, Bertrande")
                .doesNotContain("BELVA, Frederic");
        assertThat(result.getErrorOutput()).contains("2 event").contains("2 coopérateur");
    }
}
