package mx.marjan.security;

import java.sql.Connection;
import java.sql.SQLException;
import mx.marjan.shared.Database;

/** BR-22: light audit trail for important operations. */
public class AuditRepository {

    public void log(String entity, long entityId, String action, String details) {
        Database.inTransaction(connection -> {
            log(connection, entity, entityId, action, details);
            return null;
        });
    }

    public void log(Connection connection, String entity, long entityId, String action, String details)
            throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "audit_log");
        Database.update(connection, """
                INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, currentUserId(), entity, entityId, action, details);
    }

    private Long currentUserId() {
        return Session.isLoggedIn() ? Session.userId() : null;
    }
}
