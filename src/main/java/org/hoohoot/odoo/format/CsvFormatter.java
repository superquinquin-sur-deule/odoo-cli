package org.hoohoot.odoo.format;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CsvFormatter {

    private static final String SEP = ";";

    public void print(String[] headers, String[][] rows) {
        System.out.println(String.join(SEP, headers));
        for (String[] row : rows) {
            String[] escaped = new String[row.length];
            for (int i = 0; i < row.length; i++) {
                escaped[i] = escape(row[i]);
            }
            System.out.println(String.join(SEP, escaped));
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.contains(SEP) || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
