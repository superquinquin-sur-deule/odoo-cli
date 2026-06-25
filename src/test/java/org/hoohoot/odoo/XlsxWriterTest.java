package org.hoohoot.odoo;

import org.hoohoot.odoo.format.XlsxWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class XlsxWriterTest {

    @Test
    void buildsValidZipWithExpectedEntriesAndValues() throws Exception {
        XlsxWriter writer = new XlsxWriter();
        byte[] xlsx = writer.build("Produits",
                new String[]{"Nom du produit", "Créé le", "Créé par", "Quantité vendue"},
                List.of(
                        new Object[]{"Produit A", "01/05/2026", "Marie Martin", 125.219},
                        new Object[]{"Pain & Cie <bio>", "12/05/2026", "Jean", 39.0}));

        Map<String, String> entries = unzip(xlsx);

        assertThat(entries).containsKeys(
                "[Content_Types].xml",
                "_rels/.rels",
                "xl/workbook.xml",
                "xl/_rels/workbook.xml.rels",
                "xl/worksheets/sheet1.xml");

        String sheet = entries.get("xl/worksheets/sheet1.xml");
        assertThat(sheet)
                .contains("Nom du produit")
                .contains("Quantité vendue")
                .contains("Produit A")
                .contains("<v>125.219</v>")
                .contains("<v>39.0</v>")
                // l'échappement XML est appliqué aux valeurs texte
                .contains("Pain &amp; Cie &lt;bio&gt;");

        // les colonnes sont dimensionnées selon le contenu le plus long
        assertThat(sheet).contains("<cols>");
        // "Marie Martin" (12) est plus long que l'en-tête "Créé par" (8) → largeur 12+2
        assertThat(sheet).contains("<col min=\"3\" max=\"3\" width=\"14.0\" customWidth=\"1\"/>");
    }

    private static Map<String, String> unzip(byte[] bytes) throws Exception {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                entries.put(entry.getName(), out.toString(StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
