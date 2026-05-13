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
class CreneauxCreateServicesTest {

    @BeforeEach
    void setupStubs() {
        WireMockOdooResource.expect(Stub.SHIFT_TEMPLATES, Stub.CREATE_SHIFTS_WIZARD);
    }

    @Test
    @Launch({"creneaux", "create-services", "--begin-date", "06/04/2026", "--end-date", "20/07/2026"})
    void createsServices(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getErrorOutput())
                .contains("3 créneau")
                .contains("06/04/2026")
                .contains("20/07/2026");
    }

    @Test
    @Launch(value = {"creneaux", "create-services", "--begin-date", "31-12-2026", "--end-date", "20/07/2026"}, exitCode = 2)
    void rejectsInvalidDateFormat(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("mauvais format de date");
    }

    @Test
    @Launch(value = {"creneaux", "create-services", "--begin-date", "20/07/2026", "--end-date", "06/04/2026"}, exitCode = 2)
    void rejectsBeginAfterEnd(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("--begin-date");
    }

    @Test
    @Launch(value = {"creneaux", "create-services", "--begin-date", "06/04/2026"}, exitCode = 2)
    void requiresEndDate(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("--end-date");
    }
}
