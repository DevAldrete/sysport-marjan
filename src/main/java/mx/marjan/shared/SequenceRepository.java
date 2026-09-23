package mx.marjan.shared;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * The only place with SQL for key allocation (no AUTO_INCREMENT anywhere).
 * {@code next_value} holds the last id handed out; allocating increments it and
 * returns the new value, so the first id for a table is {@code max(id) + 1}.
 * The row lock taken by the UPDATE makes concurrent writers safe in a transaction.
 */
public final class SequenceRepository {

    private SequenceRepository() {}

    public static long next(Connection connection, String table) throws SQLException {
        int updated = Database.update(connection,
                "UPDATE sequences SET next_value = next_value + 1 WHERE name = ?", table);
        if (updated == 0) {
            long lastUsed = Database.queryOne(connection,
                    "SELECT COALESCE(MAX(id), 0) AS v FROM " + table,
                    rs -> rs.getLong("v")).orElse(0L);
            Database.update(connection,
                    "INSERT INTO sequences (name, next_value) VALUES (?, ?)", table, lastUsed);
            Database.update(connection,
                    "UPDATE sequences SET next_value = next_value + 1 WHERE name = ?", table);
        }
        return Database.queryOne(connection, "SELECT next_value FROM sequences WHERE name = ?",
                rs -> rs.getLong("next_value"), table).orElseThrow();
    }
}
