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
class CooperatorsExportPartsNotFoundTest {

    @BeforeEach
    void setupStubs() {
        WireMockOdooResource.expect(Stub.CAPITAL_CATEGORY_NONE);
    }

    @Test
    @Launch(value = {"cooperators", "export-parts", "--category", "Parts Z"}, exitCode = 1)
    void categoryNotFound(LaunchResult result) {
        assertThat(result.getErrorOutput()).contains("introuvable").contains("Parts Z");
    }
}
