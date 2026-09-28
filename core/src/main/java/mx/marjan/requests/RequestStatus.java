package mx.marjan.requests;

/**
 * Lifecycle states of a service request. The valid transitions (BR-03) live in
 * the database function {@code fn_request_can_transition}; the enum is only the
 * typed vocabulary used by the UI.
 */
public enum RequestStatus {
    REQUESTED("requested", "Solicitada"),
    AUTHORIZED("authorized", "Autorizada"),
    SCHEDULED("scheduled", "Programada"),
    ASSIGNED("assigned", "Asignada"),
    IN_TRANSIT("in_transit", "En transito"),
    DELIVERED("delivered", "Entregada"),
    CLOSED("closed", "Cerrada"),
    CANCELLED("cancelled", "Cancelada");

    private final String dbValue;
    private final String label;

    RequestStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static RequestStatus fromDb(String value) {
        if (value == null) {
            return REQUESTED;
        }
        return switch (value.toLowerCase()) {
            case "authorized" -> AUTHORIZED;
            case "scheduled" -> SCHEDULED;
            case "assigned" -> ASSIGNED;
            case "in_transit" -> IN_TRANSIT;
            case "delivered" -> DELIVERED;
            case "closed" -> CLOSED;
            case "cancelled" -> CANCELLED;
            default -> REQUESTED;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
