package mx.marjan.rules;

import java.sql.SQLException;
import mx.marjan.shared.Database;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseMessagesTest {

    @Test
    void duplicateKeyNamesTheFieldAndValue() {
        SQLException failure = new SQLException(
                "Duplicate entry 'ABC950101XYZ' for key 'clients.rfc'", "23000", 1062);
        String message = Database.translate(failure);
        assertTrue(message.contains("RFC"), message);
        assertTrue(message.contains("ABC950101XYZ"), message);
    }

    @Test
    void unknownUniqueStillReadsClearly() {
        SQLException failure = new SQLException(
                "Duplicate entry 'x' for key 'some.other_index'", "23000", 1062);
        assertTrue(Database.translate(failure).contains("valor unico"));
    }

    @Test
    void foreignKeyNamesTheRelationship() {
        SQLException failure = new SQLException(
                "Cannot add or update a child row: a foreign key constraint fails "
                        + "(`sysportdb`.`invoices`, CONSTRAINT `fk_invoice_request` FOREIGN KEY ...)",
                "23000", 1452);
        assertTrue(Database.translate(failure).contains("solicitud"));
    }

    @Test
    void connectionFailureIsExplained() {
        SQLException failure = new SQLException("Communications link failure", "08S01", 0);
        assertTrue(Database.translate(failure).contains("conectar"));
    }

    @Test
    void checkConstraintIsExplained() {
        SQLException failure = new SQLException(
                "Check constraint 'clients_chk_1' is violated.", "HY000", 3819);
        assertTrue(Database.translate(failure).contains("reglas"));
    }
}
