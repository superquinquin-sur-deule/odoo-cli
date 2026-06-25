package org.hoohoot.odoo.format;

import jakarta.enterprise.context.ApplicationScoped;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Écrit un fichier .xlsx minimal (OOXML) sans dépendance externe : un .xlsx est simplement
 * une archive zip de fichiers XML. Volontairement basique (pas de styles) pour rester 100%
 * compatible avec la compilation en image native GraalVM. Supporte plusieurs feuilles.
 *
 * <p>Les cellules de type {@link Number} sont écrites en numérique, tout le reste en chaîne
 * (inlineStr). Les valeurs {@code null} produisent une cellule vide. Chaque colonne est
 * dimensionnée selon la longueur de son contenu le plus long.
 */
@ApplicationScoped
public class XlsxWriter {

    /** Une feuille du classeur : un nom, des en-têtes de colonnes et des lignes de données. */
    public record Sheet(String name, String[] headers, List<Object[]> rows) {
    }

    /** Construit un classeur à une seule feuille. */
    public byte[] build(String sheetName, String[] headers, List<Object[]> rows) {
        return build(List.of(new Sheet(sheetName, headers, rows)));
    }

    /** Construit le contenu binaire d'un classeur .xlsx à partir d'une ou plusieurs feuilles. */
    public byte[] build(List<Sheet> sheets) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(baos)) {
                writeEntry(zip, "[Content_Types].xml", contentTypes(sheets.size()));
                writeEntry(zip, "_rels/.rels", rootRels());
                writeEntry(zip, "xl/workbook.xml", workbook(sheets));
                writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRels(sheets.size()));
                for (int i = 0; i < sheets.size(); i++) {
                    writeEntry(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", buildSheet(sheets.get(i)));
                }
            }
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de générer le fichier xlsx", e);
        }
    }

    private String contentTypes(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
                .append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
                .append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
                .append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Override PartName=\"/xl/worksheets/sheet").append(i)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        return sb.append("</Types>").toString();
    }

    private String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private String workbook(List<Sheet> sheets) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"")
                .append(" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        for (int i = 0; i < sheets.size(); i++) {
            sb.append("<sheet name=\"").append(escape(sheetName(sheets.get(i).name(), i)))
                    .append("\" sheetId=\"").append(i + 1).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        return sb.append("</sheets></workbook>").toString();
    }

    private String workbookRels(int sheetCount) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 1; i <= sheetCount; i++) {
            sb.append("<Relationship Id=\"rId").append(i)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i).append(".xml\"/>");
        }
        return sb.append("</Relationships>").toString();
    }

    private String buildSheet(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
                .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");

        appendColumns(sb, sheet.headers(), sheet.rows());

        sb.append("<sheetData>");
        int rowNum = 1;
        appendRow(sb, rowNum++, sheet.headers().clone());
        for (Object[] row : sheet.rows()) {
            appendRow(sb, rowNum++, row);
        }
        return sb.append("</sheetData></worksheet>").toString();
    }

    /**
     * Définit la largeur de chaque colonne en fonction de la longueur du contenu le plus long
     * (en-tête comprise), pour que les colonnes soient lisibles sans redimensionnement manuel.
     * La largeur est exprimée en nombre de caractères, avec une marge et des bornes [8, 60].
     */
    private void appendColumns(StringBuilder sb, String[] headers, List<Object[]> rows) {
        int columns = headers.length;
        if (columns == 0) {
            return;
        }
        int[] maxLen = new int[columns];
        for (int c = 0; c < columns; c++) {
            maxLen[c] = headers[c] == null ? 0 : headers[c].length();
        }
        for (Object[] row : rows) {
            for (int c = 0; c < columns && c < row.length; c++) {
                if (row[c] != null) {
                    maxLen[c] = Math.max(maxLen[c], row[c].toString().length());
                }
            }
        }

        sb.append("<cols>");
        for (int c = 0; c < columns; c++) {
            double width = Math.min(60, Math.max(8, maxLen[c] + 2));
            sb.append("<col min=\"").append(c + 1).append("\" max=\"").append(c + 1)
                    .append("\" width=\"").append(width).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols>");
    }

    private void appendRow(StringBuilder sb, int rowNum, Object[] values) {
        sb.append("<row r=\"").append(rowNum).append("\">");
        for (int col = 0; col < values.length; col++) {
            Object value = values[col];
            if (value == null) {
                continue;
            }
            String ref = colName(col) + rowNum;
            if (value instanceof Number n) {
                sb.append("<c r=\"").append(ref).append("\"><v>").append(n).append("</v></c>");
            } else {
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escape(value.toString())).append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    private static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static String sheetName(String name, int index) {
        if (name == null || name.isBlank()) {
            return "Feuille" + (index + 1);
        }
        return name.length() > 31 ? name.substring(0, 31) : name;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private void writeEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
