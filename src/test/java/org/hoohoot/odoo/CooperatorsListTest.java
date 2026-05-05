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
class CooperatorsListTest {

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
}
