package mx.marjan.security;

import java.sql.Connection;
import java.sql.SQLException;
import mx.marjan.shared.Database;

/** BR-22: light audit trail for important operations. */
public class AuditRepository {

    public void log(String entity, long entityId, String action, String details) {
        Database.insert("""
                INSERT INTO audit_log (user_id, entity, entity_id, action, details)
                VALUES (?, ?, ?, ?, ?)
                """, currentUserId(), entity, entityId, action, details);
    }

    public void log(Connection connection, String entity, long entityId, String action, String details)
            throws SQLException {
        Database.insertReturningId(connection, """
                INSERT INTO audit_log (user_id, entity, entity_id, action, details)
                VALUES (?, ?, ?, ?, ?)
                """, currentUserId(), entity, entityId, action, details);
    }

    private Long currentUserId() {
        return Session.isLoggedIn() ? Session.userId() : null;
    }
}
