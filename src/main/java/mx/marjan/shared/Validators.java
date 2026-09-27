package mx.marjan.shared;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * The only format checks the application performs before hitting the database.
 * Domain validation (RFC, CURP, email, ranges, ...) lives in the schema as
 * {@code fn_*_valid} functions, which are the single source of truth.
 */
public final class Validators {

    private static final Pattern USERNAME =
            Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    private Validators() {}

    public static boolean isValidUsername(String value) {
        return value != null && !value.isBlank() && USERNAME.matcher(value.trim()).matches();
    }

    /** Live "is this a date" check for {@link FormPanel#validate}. Null means valid. */
    public static Function<String, String> date() {
        return value -> value == null || value.isBlank() || Dates.parseDate(value).isPresent()
                ? null : "Formato esperado: AAAA-MM-DD";
    }

    /** Live "is this a date and time" check. Null means valid. */
    public static Function<String, String> dateTime() {
        return value -> value == null || value.isBlank() || Dates.parseDateTime(value).isPresent()
                ? null : "Formato esperado: AAAA-MM-DD HH:MM";
    }

    /** Live money check. Null means valid. */
    public static Function<String, String> money() {
        return value -> value == null || value.isBlank() || Money.parse(value).isPresent()
                ? null : "Importe invalido, ej. 1500.00";
    }

    /** Live decimal check. Null means valid. */
    public static Function<String, String> number() {
        return value -> value == null || value.isBlank() || Numbers.parseOrZero(value) != null
                ? null : "Numero invalido";
    }
}
