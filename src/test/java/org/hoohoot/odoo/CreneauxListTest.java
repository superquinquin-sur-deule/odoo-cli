package org.hoohoot.odoo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.hoohoot.odoo.test.WireMockOdooResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@QuarkusTestResource(WireMockOdooResource.class)
class CreneauxListTest {

    @Test
    @Launch({"creneaux", "list", "--output", "csv"})
    void listAll(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Mon AM")
                .contains("Mon PM")
                .contains("Tue AM");
        assertThat(result.getErrorOutput()).endsWith("3 créneau(x)");
    }

    @Test
    @Launch({"creneaux", "list", "--output", "csv", "--under-min"})
    void underMin(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("Mon AM")
                .doesNotContain("Mon PM")
                .doesNotContain("Tue AM");
        assertThat(result.getErrorOutput()).endsWith("1 créneau");
    }
}
