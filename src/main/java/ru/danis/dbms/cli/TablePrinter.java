package ru.danis.dbms.cli;

import ru.danis.dbms.model.QueryResult;
import ru.danis.dbms.model.Row;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility for formatting database query results into aligned ASCII tables.
 */
public class TablePrinter {

    private TablePrinter() {
    }

    /**
     * Prints a formatted ASCII representation of the query result to the given stream.
     *
     * @param result execution result to display
     * @param out target output stream
     */
    public static void print(QueryResult result, PrintStream out) {
        if (!result.isSelect()) {
            out.printf(Locale.US, "OK. %s (affected: %d) [%.2f ms]%n",
                    result.getMessage(),
                    result.getRowsAffected(),
                    result.getExecutionTimeMs());
            return;
        }

        List<String> headers = result.getColumnNames();
        List<Row> rows = result.getRows();

        if (headers.isEmpty()) {
            out.printf("Empty set (0.00 ms)%n");
            return;
        }

        int[] colWidths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            colWidths[i] = Math.max(headers.get(i).length(), 4);
        }

        List<String[]> formattedRows = new ArrayList<>(rows.size());
        for (Row row : rows) {
            String[] rowData = new String[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                Object val = row.get(headers.get(i));
                String str = val == null ? "NULL" : val.toString();
                rowData[i] = str;
                if (str.length() > colWidths[i]) {
                    colWidths[i] = str.length();
                }
            }
            formattedRows.add(rowData);
        }

        String border = buildBorder(colWidths);
        out.println(border);

        StringBuilder headerLine = new StringBuilder("|");
        for (int i = 0; i < headers.size(); i++) {
            headerLine.append(String.format(" %-" + colWidths[i] + "s |", headers.get(i)));
        }
        out.println(headerLine);
        out.println(border);

        if (!formattedRows.isEmpty()) {
            for (String[] rowData : formattedRows) {
                StringBuilder rowLine = new StringBuilder("|");
                for (int i = 0; i < headers.size(); i++) {
                    rowLine.append(String.format(" %-" + colWidths[i] + "s |", rowData[i]));
                }
                out.println(rowLine);
            }
            out.println(border);
        }
        out.printf(Locale.US, "%d row(s) returned (%.2f ms)%n", rows.size(), result.getExecutionTimeMs());
        if (result.getExecutionPlan() != null) {
            out.printf("Execution Plan: %s%n", result.getExecutionPlan());
        }
    }

    private static String buildBorder(int[] colWidths) {
        StringBuilder sb = new StringBuilder("+");
        for (int width : colWidths) {
            sb.append("-".repeat(width + 2)).append("+");
        }
        return sb.toString();
    }
}
