package mx.marjan.shared;

/** Helpers to render records and free text compactly, so tables and lists stay readable. */
public final class Text {

    /** Longest label a combo/detail surfaces by default. */
    public static final int DEFAULT_MAX = 40;

    private Text() {}

    /** Collapses whitespace and cuts a value to {@code max} characters, adding an ellipsis. */
    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        if (max <= 0 || compact.length() <= max) {
            return compact;
        }
        return compact.substring(0, max - 1) + "\u2026";
    }

    /** Renders any value as a compact one-line label, empty when null. */
    public static String label(Object value) {
        return value == null ? "" : truncate(String.valueOf(value), DEFAULT_MAX);
    }
}
