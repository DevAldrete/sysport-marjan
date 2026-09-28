package mx.marjan.reports;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/** FR-RPT-7: exports any report to a CSV file. */
public final class CsvExporter {

    private CsvExporter() {}

    public static void write(Report report, File file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            writer.write(line(report.headers()));
            for (List<Object> row : report.rows()) {
                writer.write(line(row));
            }
        }
    }

    private static String line(List<?> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(values.get(i)));
        }
        return builder.append(System.lineSeparator()).toString();
    }

    private static String escape(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
