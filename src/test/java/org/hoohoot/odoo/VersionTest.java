package org.hoohoot.odoo;

import io.quarkus.test.junit.main.Launch;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
class VersionTest {

    @Test
    @Launch({"--version"})
    void versionReflectsPomProjectVersion(LaunchResult result) {
        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("odoo-cli")
                .contains("1.0.0-SNAPSHOT");
    }
}
