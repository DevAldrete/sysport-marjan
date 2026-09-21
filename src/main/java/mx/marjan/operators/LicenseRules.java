package mx.marjan.operators;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.shared.Result;

/** BR-09 / BR-10: license validity and expiry warnings. Pure, no I/O. */
public final class LicenseRules {

    public static final int WARNING_DAYS = 30;

    private LicenseRules() {}

    public static boolean isExpired(License license, LocalDate today) {
        return license != null && license.expirationDate() != null && license.expirationDate().isBefore(today);
    }

    public static boolean expiresWithin(License license, int days, LocalDate today) {
        if (license == null || license.expirationDate() == null || isExpired(license, today)) {
            return false;
        }
        long remaining = ChronoUnit.DAYS.between(today, license.expirationDate());
        return remaining <= days;
    }

    /** The operator must hold a license valid through the planned end date. */
    public static Result<Void> canAssign(Employee operator, LocalDate plannedEnd, LocalDate today) {
        List<String> problems = new ArrayList<>();
        License license = operator.license();
        if (license == null || license.expirationDate() == null) {
            problems.add("El operador no tiene licencia registrada");
        } else if (license.expirationDate().isBefore(plannedEnd)) {
            problems.add("La licencia del operador " + operator.name() + " vence el "
                    + license.expirationDate() + ", antes del fin del viaje");
        }
        return problems.isEmpty() ? Result.ok(null) : Result.err(problems);
    }

    public static String expiryLabel(License license, LocalDate today) {
        if (license == null || license.expirationDate() == null) {
            return "Sin licencia";
        }
        if (isExpired(license, today)) {
            return "VENCIDA (" + license.expirationDate() + ")";
        }
        if (expiresWithin(license, WARNING_DAYS, today)) {
            return "Por vencer (" + license.expirationDate() + ")";
        }
        return license.expirationDate().toString();
    }
}
