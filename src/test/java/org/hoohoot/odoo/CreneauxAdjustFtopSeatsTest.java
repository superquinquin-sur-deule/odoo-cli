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
class CreneauxAdjustFtopSeatsTest {

    @Test
    void adjustsFtopSeatsForEachShift(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.CONFIRMED_SHIFTS_WITH_TICKETS,
                Stub.SHIFT_TICKETS_READ,
                Stub.SHIFT_TEMPLATE_TICKETS_READ,
                Stub.SHIFT_TICKET_WRITE);

        LaunchResult result = launcher.launch(
                "creneaux", "adjust-ftop-seats", "--begin-date", "20/04/2026", "--end-date", "20/07/2026");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Mon AM 20/04")
                .contains("4 → 3")
                .contains("Mon PM 20/04")
                .contains("5 → 1");
        assertThat(result.getErrorOutput()).contains("2 ajustement");
    }

    @Test
    void dryRunDoesNotWrite(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.CONFIRMED_SHIFTS_WITH_TICKETS,
                Stub.SHIFT_TICKETS_READ,
                Stub.SHIFT_TEMPLATE_TICKETS_READ);

        LaunchResult result = launcher.launch(
                "creneaux", "adjust-ftop-seats", "--begin-date", "20/04/2026", "--end-date", "20/07/2026", "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("[dry-run]")
                .contains("4 → 3")
                .contains("5 → 1");
        assertThat(result.getErrorOutput()).contains("dry-run").contains("simulé");
    }

    @Test
    void includeDraftAlsoTreatsDraftShifts(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(
                Stub.SHIFTS_STATE_IN_MATCH,
                Stub.DRAFT_SHIFT_TICKETS_READ,
                Stub.SHIFT_TEMPLATE_TICKETS_READ,
                Stub.SHIFT_TICKET_WRITE);

        LaunchResult result = launcher.launch(
                "creneaux", "adjust-ftop-seats",
                "--begin-date", "20/04/2026", "--end-date", "20/07/2026",
                "--include-draft");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Tue AM 27/04")
                .contains("4 → 6");
        assertThat(result.getErrorOutput()).contains("1 ajustement");
    }

    @Test
    void reportsZeroWhenNoShifts(QuarkusMainLauncher launcher) {
        WireMockOdooResource.expect(Stub.NO_SHIFTS);

        LaunchResult result = launcher.launch(
                "creneaux", "adjust-ftop-seats", "--begin-date", "20/04/2026", "--end-date", "20/07/2026");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput()).contains("0 service");
    }

    @Test
    @Launch(value = {"creneaux", "adjust-ftop-seats", "--begin-date", "31-12-2026", "--end-date", "20/07/2026"}, exitCode = 2)
    void rejectsInvalidDateFormat(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("mauvais format de date");
    }

    @Test
    @Launch(value = {"creneaux", "adjust-ftop-seats", "--begin-date", "20/07/2026", "--end-date", "20/04/2026"}, exitCode = 2)
    void rejectsBeginAfterEnd(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("--begin-date");
    }

    @Test
    @Launch(value = {"creneaux", "adjust-ftop-seats", "--begin-date", "20/04/2026"}, exitCode = 2)
    void requiresEndDate(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("--end-date");
    }
}
