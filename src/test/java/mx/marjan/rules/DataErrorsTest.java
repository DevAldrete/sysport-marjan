package mx.marjan.rules;

import java.sql.SQLException;
import mx.marjan.shared.Database;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DataErrorsTest {

    @Test
    void translatesCommonDriverErrors() {
        assertTrue(Database.translate(new SQLException("dup", "23000", 1062)).contains("valor unico"));
        assertTrue(Database.translate(new SQLException("range", "22003", 1264)).contains("reglas"));
        assertTrue(Database.translate(new SQLException("long", "22001", 1406)).contains("largo"));
        assertTrue(Database.translate(new SQLException("fk", "23000", 1451)).contains("eliminar"));
        assertTrue(Database.translate(new SQLException("fk", "23000", 1452)).contains("relacionado"));
    }
}
