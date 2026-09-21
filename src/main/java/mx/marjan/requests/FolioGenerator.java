package mx.marjan.requests;

/** BR-01: human-readable unique folio, e.g. SR-2026-000123. */
public final class FolioGenerator {

    private static final String PREFIX = "SR";

    private FolioGenerator() {}

    public static String format(int year, long sequence) {
        return String.format("%s-%d-%06d", PREFIX, year, sequence);
    }

    public static int yearOf(String folio) {
        String[] parts = folio.split("-");
        return parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
    }
}
