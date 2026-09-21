package mx.marjan.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/** Date helpers. Parsing is lenient, formatting is fixed and ISO-friendly. */
public final class Dates {

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private Dates() {}

    public static String format(LocalDate date) {
        return date == null ? "" : DATE.format(date);
    }

    public static String format(LocalDateTime dateTime) {
        return dateTime == null ? "" : DATE_TIME.format(dateTime);
    }

    public static Optional<LocalDate> parseDate(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text.trim(), DATE));
        } catch (DateTimeParseException failure) {
            return Optional.empty();
        }
    }

    public static Optional<LocalDateTime> parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String value = text.trim().replace('T', ' ');
        try {
            if (value.length() <= 10) {
                return Optional.of(LocalDate.parse(value, DATE).atStartOfDay());
            }
            if (value.length() == 16) {
                return Optional.of(LocalDateTime.parse(value, DATE_TIME));
            }
            return Optional.of(LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        } catch (DateTimeParseException failure) {
            return Optional.empty();
        }
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }
}
