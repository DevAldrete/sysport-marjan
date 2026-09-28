package mx.marjan.reports;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.util.List;

/** FR-RPT-7: exports any report to CSV, on disk or to any writer (e.g. an HTTP response). */
public final class CsvExporter {

    private CsvExporter() {}

    public static void write(Report report, File file) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath())) {
            write(report, writer);
        }
    }

    public static void write(Report report, Writer writer) throws IOException {
        writer.write(line(report.headers()));
        for (List<Object> row : report.rows()) {
            writer.write(line(row));
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
