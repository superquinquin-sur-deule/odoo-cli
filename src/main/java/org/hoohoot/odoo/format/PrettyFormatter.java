package org.hoohoot.odoo.format;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PrettyFormatter {

    public void print(String[] headers, String[][] rows, boolean[] rightAlign) {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = headers[i].length();
        }
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                widths[i] = Math.max(widths[i], row[i].length());
            }
        }

        printRow(headers, widths, rightAlign);
        String[] sep = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            sep[i] = "-".repeat(widths[i]);
        }
        printRow(sep, widths, new boolean[headers.length]);
        for (String[] row : rows) {
            printRow(row, widths, rightAlign);
        }
    }

    private static void printRow(String[] cells, int[] widths, boolean[] rightAlign) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            String fmt = "%" + (rightAlign[i] ? "" : "-") + widths[i] + "s";
            sb.append(String.format(fmt, cells[i]));
            if (i < cells.length - 1) sb.append("  ");
        }
        System.out.println(sb);
    }
}
