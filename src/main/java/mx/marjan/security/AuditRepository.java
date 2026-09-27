package mx.marjan.security;

import mx.marjan.shared.Database;

/** BR-22: light audit trail for important operations (stored procedure). */
public class AuditRepository {

    public void log(String entity, long entityId, String action, String details) {
        Database.callNoOut("{call sp_audit_log(?,?,?,?,?)}",
                Session.isLoggedIn() ? Session.userId() : null, entity, entityId, action, details);
    }
}
