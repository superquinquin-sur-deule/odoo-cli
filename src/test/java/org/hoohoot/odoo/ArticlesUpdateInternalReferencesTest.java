package org.hoohoot.odoo;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.main.LaunchResult;
import io.quarkus.test.junit.main.QuarkusMainLauncher;
import io.quarkus.test.junit.main.QuarkusMainTest;
import org.hoohoot.odoo.test.WireMockOdooResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@QuarkusMainTest
@QuarkusTestResource(WireMockOdooResource.class)
class ArticlesUpdateInternalReferencesTest {

    @Test
    void updatesFoundProduct(QuarkusMainLauncher launcher, @TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("products.csv");
        Files.writeString(csv, "Name,InternalReference\nPommes,REF-001\n");

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Pommes -> REF-001");
        assertThat(result.getErrorOutput()).contains("1 mise(s) à jour");
        assertThat(result.getErrorOutput()).contains("0 non trouvée(s)");
        assertThat(result.getErrorOutput()).contains("0 ambigu");
    }

    @Test
    void reportsMissingAndAmbiguous(QuarkusMainLauncher launcher, @TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("products.csv");
        Files.writeString(csv, """
                Name,InternalReference
                Pommes,REF-001
                Inconnu,REF-002
                "Bananes, lot",REF-003
                """);

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString());

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput()).contains("Pommes -> REF-001");
        assertThat(result.getErrorOutput())
                .contains("Inconnu")
                .contains("Bananes, lot")
                .contains("=== Lignes non mises à jour ===")
                .contains("[non trouvé] Inconnu,REF-002")
                .contains("[ambigu]     Bananes, lot,REF-003")
                .contains("1 mise(s) à jour")
                .contains("1 non trouvée(s)")
                .contains("1 ambigu");
    }

    @Test
    void dryRunDoesNotWriteAndReportsSimulation(QuarkusMainLauncher launcher, @TempDir Path tmp) throws Exception {
        Path csv = tmp.resolve("products.csv");
        Files.writeString(csv, """
                Name,InternalReference
                Pommes,REF-001
                Inconnu,REF-002
                "Bananes, lot",REF-003
                """);

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString(), "--dry-run");

        assertThat(result.exitCode()).isZero();
        assertThat(result.getOutput())
                .contains("[dry-run] Pommes : OLD -> REF-001")
                .doesNotContain("Pommes -> REF-001");
        assertThat(result.getErrorOutput())
                .contains("Mode dry-run : aucune modification effectuée")
                .contains("1 mise(s) à jour simulée(s)")
                .contains("1 non trouvée(s)")
                .contains("1 ambigu");
    }

    @Test
    void failsWhenCsvMissing(QuarkusMainLauncher launcher, @TempDir Path tmp) {
        Path csv = tmp.resolve("missing.csv");

        LaunchResult result = launcher.launch(
                "articles", "update-internal-references", "--csv", csv.toString());

        assertThat(result.exitCode()).isNotZero();
    }
}
