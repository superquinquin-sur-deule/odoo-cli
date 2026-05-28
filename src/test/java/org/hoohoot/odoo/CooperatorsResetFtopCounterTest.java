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
                Stub.SHIFT_COUNTER_EVENT_WRITE,
                Stub.FTOP_PARTNERS_DISPLAY_READ,
                Stub.SHIFT_COUNTER_EVENT_CREATE);

        LaunchResult result = launcher.launch("cooperators", "reset-ftop-counter");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("ARDOUIN PERO, Joëlle")
                .contains("BELVA, Frederic")
                .contains("LONGUEVAL, Bertrande")
                .contains("→ 0");
        assertThat(result.getErrorOutput()).contains("3 event").contains("3 compteur");
    }

    @Test
    void dryRunDoesNotWrite(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.FTOP_COUNTER_EVENTS_READ,
                Stub.FTOP_PARTNERS_DISPLAY_READ);

        LaunchResult result = launcher.launch("cooperators", "reset-ftop-counter", "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("[dry-run]");
        assertThat(result.getErrorOutput()).contains("dry-run").contains("simulé");
    }

    @Test
    void reportsZeroWhenNothingToDo(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(Stub.NO_COUNTER_EVENTS, Stub.NO_FTOP_PARTNERS);

        LaunchResult result = launcher.launch("cooperators", "reset-ftop-counter");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput()).contains("0 event").contains("0 compteur");
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
                Stub.SHIFT_COUNTER_EVENT_WRITE,
                Stub.FTOP_PARTNERS_DISPLAY_PARTNER_FILTER,
                Stub.SHIFT_COUNTER_EVENT_CREATE);

        LaunchResult result = launcher.launch(
                "cooperators", "reset-ftop-counter",
                "--partner-id", "1689", "--partner-id", "1879");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("ARDOUIN PERO, Joëlle")
                .contains("LONGUEVAL, Bertrande")
                .doesNotContain("BELVA, Frederic");
        assertThat(result.getErrorOutput()).contains("2 event").contains("2 compteur");
    }
}
