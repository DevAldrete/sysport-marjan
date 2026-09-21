package mx.marjan.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Optional;

/** Money is always BigDecimal, scaled to two decimals for display and storage. */
public final class Money {

    private static final ThreadLocal<DecimalFormat> FORMAT =
            ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));

    private Money() {}

    public static BigDecimal of(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal scaled(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static String format(BigDecimal value) {
        return "$" + FORMAT.get().format(zeroIfNull(value));
    }

    public static Optional<BigDecimal> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            String cleaned = text.replace("$", "").replace(",", "").trim();
            return Optional.of(new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP));
        } catch (NumberFormatException failure) {
            return Optional.empty();
        }
    }
}
