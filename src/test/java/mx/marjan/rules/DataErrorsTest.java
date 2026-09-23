package mx.marjan.rules;

import java.sql.SQLException;
import mx.marjan.shared.Database;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataErrorsTest {

    @Test
    void translatesCommonDriverErrors() {
        assertEquals("Ya existe un registro con ese valor unico.",
                Database.translate(new SQLException("dup", "23000", 1062)));
        assertTrue(Database.translate(new SQLException("range", "22003", 1264)).contains("rango"));
        assertTrue(Database.translate(new SQLException("long", "22001", 1406)).contains("largo"));
        assertTrue(Database.translate(new SQLException("fk", "23000", 1451)).contains("eliminar"));
        assertTrue(Database.translate(new SQLException("fk", "23000", 1452)).contains("referencia"));
    }
}
