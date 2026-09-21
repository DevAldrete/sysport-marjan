package mx.marjan.rules;

import java.time.LocalDate;
import mx.marjan.operators.Employee;
import mx.marjan.operators.EmployeeStatus;
import mx.marjan.operators.License;
import mx.marjan.operators.LicenseRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseRulesTest {

    private final LocalDate today = LocalDate.of(2026, 6, 1);

    private License license(LocalDate expiry) {
        return new License(1, "LIC-1", "Federal", LocalDate.of(2020, 1, 1), expiry);
    }

    private Employee operator(License license) {
        return new Employee(1, "Operador", "", "555", "", "", "", "", "", license,
                EmployeeStatus.AVAILABLE);
    }

    @Test
    void detectsExpiredLicense() { // BR-10
        assertTrue(LicenseRules.isExpired(license(today.minusDays(1)), today));
        assertFalse(LicenseRules.isExpired(license(today.plusDays(1)), today));
    }

    @Test
    void warnsWithinThirtyDays() { // BR-10
        assertTrue(LicenseRules.expiresWithin(license(today.plusDays(10)), 30, today));
        assertFalse(LicenseRules.expiresWithin(license(today.plusDays(60)), 30, today));
        assertFalse(LicenseRules.expiresWithin(license(today.minusDays(1)), 30, today));
    }

    @Test
    void blocksAssignmentWhenLicenseExpiresBeforeTrip() { // BR-09
        assertTrue(LicenseRules.canAssign(operator(license(today.plusDays(5))), today.plusDays(10), today).isErr());
        assertTrue(LicenseRules.canAssign(operator(license(today.plusDays(40))), today.plusDays(10), today).isOk());
    }

    @Test
    void blocksOperatorWithoutLicense() {
        assertTrue(LicenseRules.canAssign(operator(null), today.plusDays(1), today).isErr());
    }
}
