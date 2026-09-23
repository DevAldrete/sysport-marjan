package mx.marjan.shared;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.regex.Pattern;

/** Reusable format and range checks used by the rule classes. */
public final class Validators {

    private static final Pattern EMAIL =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern RFC =
            Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$");
    private static final Pattern CURP =
            Pattern.compile("^[A-Z]{4}\\d{6}[HM][A-Z]{5}[A-Z0-9]\\d$");
    private static final Pattern PHONE =
            Pattern.compile("^[0-9+()\\-\\s.]{7,20}$");
    private static final Pattern PLATES =
            Pattern.compile("^[A-Z0-9\\-]{4,10}$");
    private static final Pattern LICENSE =
            Pattern.compile("^[A-Za-z0-9\\-]{4,30}$");
    private static final Pattern USERNAME =
            Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    public static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    public static final LocalDate MAX_DATE = LocalDate.of(2100, 1, 1);
    public static final int MIN_VEHICLE_YEAR = 1950;

    /** Column limits, so values never reach the database out of range. */
    public static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");
    public static final BigDecimal MAX_LITERS = new BigDecimal("999999.99");
    public static final BigDecimal MAX_PRICE_PER_LITER = new BigDecimal("99999.999");
    public static final BigDecimal MAX_MEASURE = new BigDecimal("999999999.9");
    public static final int MAX_CREDIT_DAYS = 3650;

    private Validators() {}

    /** Blank values are allowed for optional fields; only a present value is validated. */
    public static boolean isValidEmail(String value) {
        return blank(value) || EMAIL.matcher(value.trim()).matches();
    }

    public static boolean isValidRfc(String value) {
        return blank(value) || RFC.matcher(value.trim().toUpperCase()).matches();
    }

    public static boolean isValidCurp(String value) {
        return blank(value) || CURP.matcher(value.trim().toUpperCase()).matches();
    }

    public static boolean isValidPhone(String value) {
        return blank(value) || PHONE.matcher(value.trim()).matches();
    }

    public static boolean isValidPlates(String value) {
        return blank(value) || PLATES.matcher(value.trim().toUpperCase()).matches();
    }

    public static boolean isValidLicenseNumber(String value) {
        return blank(value) || LICENSE.matcher(value.trim()).matches();
    }

    public static boolean isValidUsername(String value) {
        return !blank(value) && USERNAME.matcher(value.trim()).matches();
    }

    /** Rejects absurd or negative dates such as years before 2000. */
    public static boolean isValidDate(LocalDate value) {
        return value == null || (!value.isBefore(MIN_DATE) && !value.isAfter(MAX_DATE));
    }

    public static boolean isValidYear(Integer year) {
        return year == null || (year >= MIN_VEHICLE_YEAR && year <= LocalDate.now().getYear() + 1);
    }

    public static boolean isNonNegative(BigDecimal value) {
        return value == null || value.signum() >= 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    /** Any DECIMAL(12,2) column (money, rates, amounts). */
    public static boolean isMoney(BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.compareTo(MAX_MONEY) <= 0);
    }

    /** Any DECIMAL(10,1) column (km, weight, odometer). */
    public static boolean isMeasure(BigDecimal value) {
        return value == null || (value.signum() >= 0 && value.compareTo(MAX_MEASURE) <= 0);
    }

    /** DECIMAL(8,2) liters. */
    public static boolean isLiters(BigDecimal value) {
        return value == null || (value.signum() > 0 && value.compareTo(MAX_LITERS) <= 0);
    }

    /** DECIMAL(8,3) price per liter. */
    public static boolean isPricePerLiter(BigDecimal value) {
        return value == null || (value.signum() > 0 && value.compareTo(MAX_PRICE_PER_LITER) <= 0);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
