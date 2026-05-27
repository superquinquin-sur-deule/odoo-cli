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
class CreneauxAlertTest {

    @BeforeEach
    void setupStubs() {
        WireMockOdooResource.expect(Stub.SHIFTS_FOR_ALERT);
    }

    @Test
    @Launch({"creneaux", "alert", "--output", "csv"})
    void listsOnlyUnderMinShifts(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("CMar. - 13:15")
                .contains("CMar. - 15:45")
                .doesNotContain("CMar. - 18:15")
                .doesNotContain("Volants CMar.");
        assertThat(result.getErrorOutput()).contains("2 créneau");
    }

    @Test
    @Launch({"creneaux", "alert"})
    void prettyOutputByDefault(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("CMar. - 13:15").contains("CMar. - 15:45");
    }

    @Test
    @Launch({"creneaux", "alert", "--output", "json"})
    void jsonOutput(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .startsWith("[")
                .contains("\"id\":401")
                .contains("\"name\":\"CMar. - 13:15\"")
                .contains("\"seats_min\":4")
                .contains("\"seats_reserved\":1")
                .contains("\"id\":402")
                .doesNotContain("\"id\":403")
                .doesNotContain("\"id\":404");
        assertThat(result.getErrorOutput()).contains("2 créneau");
    }

    @Test
    @Launch({"creneaux", "alert", "--begin-date", "19/05/2026", "--end-date", "21/05/2026", "--output", "csv"})
    void acceptsDateOverrides(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("CMar. - 13:15")
                .contains("CMar. - 15:45");
        assertThat(result.getErrorOutput()).contains("2 créneau");
    }

    @Test
    @Launch(value = {"creneaux", "alert", "--begin-date", "31-12-2026"}, exitCode = 2)
    void rejectsInvalidBeginDate(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("mauvais format de date");
    }

    @Test
    @Launch(value = {"creneaux", "alert", "--end-date", "31-12-2026"}, exitCode = 2)
    void rejectsInvalidEndDate(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("mauvais format de date");
    }

    @Test
    @Launch(value = {"creneaux", "alert", "--begin-date", "20/07/2026", "--end-date", "20/04/2026"}, exitCode = 2)
    void rejectsBeginAfterEnd(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("--begin-date");
    }
}
