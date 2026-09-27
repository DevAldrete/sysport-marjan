package mx.marjan.shared;

import java.util.regex.Pattern;

/**
 * The only format check the application performs before hitting the database.
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
}
