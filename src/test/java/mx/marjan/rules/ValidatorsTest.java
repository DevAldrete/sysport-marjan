package mx.marjan.rules;

import java.time.LocalDate;
import mx.marjan.shared.Validators;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorsTest {

    @Test
    void emailFormat() {
        assertTrue(Validators.isValidEmail("compras@dnorte.mx"));
        assertTrue(Validators.isValidEmail(""));
        assertFalse(Validators.isValidEmail("not-an-email"));
        assertFalse(Validators.isValidEmail("a@b"));
    }

    @Test
    void rfcFormat() {
        assertTrue(Validators.isValidRfc("DNO950101AAA"));
        assertTrue(Validators.isValidRfc("ABCD950101XYZ"));
        assertFalse(Validators.isValidRfc("ABC12"));
        assertFalse(Validators.isValidRfc("XX"));
    }

    @Test
    void curpFormat() {
        assertTrue(Validators.isValidCurp("MARI800101HDFRSS01"));
        assertFalse(Validators.isValidCurp("123"));
    }

    @Test
    void phoneAndPlates() {
        assertTrue(Validators.isValidPhone("555 123 4567"));
        assertFalse(Validators.isValidPhone("abc"));
        assertTrue(Validators.isValidPlates("ABC-123-A"));
        assertFalse(Validators.isValidPlates("!"));
    }

    @Test
    void dateRangeRejectsAbsurdValues() {
        assertTrue(Validators.isValidDate(LocalDate.of(2026, 1, 1)));
        assertFalse(Validators.isValidDate(LocalDate.of(1899, 1, 1)));
        assertFalse(Validators.isValidDate(LocalDate.of(2200, 1, 1)));
    }

    @Test
    void vehicleYearRange() {
        assertTrue(Validators.isValidYear(2020));
        assertFalse(Validators.isValidYear(1800));
        assertFalse(Validators.isValidYear(2999));
    }

    @Test
    void columnRanges() {
        assertTrue(Validators.isMoney(new java.math.BigDecimal("9999999999.99")));
        assertFalse(Validators.isMoney(new java.math.BigDecimal("10000000000")));
        assertTrue(Validators.isMeasure(new java.math.BigDecimal("999999999.9")));
        assertFalse(Validators.isMeasure(new java.math.BigDecimal("1000000000")));
        assertTrue(Validators.isLiters(new java.math.BigDecimal("100")));
        assertFalse(Validators.isLiters(java.math.BigDecimal.ZERO));
        assertTrue(Validators.isPricePerLiter(new java.math.BigDecimal("24.5")));
    }
}
