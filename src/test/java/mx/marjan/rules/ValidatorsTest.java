package mx.marjan.rules;

import mx.marjan.shared.Validators;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorsTest {

    @Test
    void usernameFormat() {
        assertTrue(Validators.isValidUsername("admin"));
        assertTrue(Validators.isValidUsername("ana.rivas-07"));
        assertFalse(Validators.isValidUsername(null));
        assertFalse(Validators.isValidUsername(""));
        assertFalse(Validators.isValidUsername("ab"));
        assertFalse(Validators.isValidUsername("bad user!"));
    }
}
