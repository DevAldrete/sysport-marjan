package mx.marjan.reports;

import java.util.List;

/** A generic, displayable and exportable report: a header row plus rows of values. */
public record Report(String title, List<String> headers, List<List<Object>> rows) {

    public Report {
        headers = List.copyOf(headers);
        rows = List.copyOf(rows);
    }
}
