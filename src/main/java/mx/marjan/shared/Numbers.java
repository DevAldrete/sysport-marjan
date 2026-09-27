package mx.marjan.shared;

import java.math.BigDecimal;

/** Parsing and formatting for the decimal input fields used across the views. */
public final class Numbers {

    private Numbers() {}

    /**
     * Parses a decimal field, treating blank as zero and accepting thousands
     * separators. Returns null when the text is present but not a number, so
     * the caller can report the problem instead of silently using zero.
     */
    public static BigDecimal parseOrZero(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.replace(",", "").trim());
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    /** Blank-safe plain text for a decimal column. */
    public static String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
