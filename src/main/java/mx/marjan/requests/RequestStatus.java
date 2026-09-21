package mx.marjan.requests;

import java.util.Set;

/** BR-03: the only valid lifecycle transitions for a service request (PRD 5.1). */
public enum RequestStatus {
    REQUESTED("requested", "Solicitada"),
    AUTHORIZED("authorized", "Autorizada"),
    SCHEDULED("scheduled", "Programada"),
    ASSIGNED("assigned", "Asignada"),
    IN_TRANSIT("in_transit", "En transito"),
    DELIVERED("delivered", "Entregada"),
    CLOSED("closed", "Cerrada"),
    CANCELLED("cancelled", "Cancelada");

    private static final Set<RequestStatus> CANCELLABLE =
            Set.of(REQUESTED, AUTHORIZED, SCHEDULED, ASSIGNED);

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

    public boolean canMoveTo(RequestStatus next) {
        if (next == CANCELLED) {
            return CANCELLABLE.contains(this);
        }
        return switch (this) {
            case REQUESTED -> next == AUTHORIZED;
            case AUTHORIZED -> next == SCHEDULED;
            case SCHEDULED -> next == ASSIGNED;
            case ASSIGNED -> next == IN_TRANSIT;
            case IN_TRANSIT -> next == DELIVERED;
            case DELIVERED -> next == CLOSED;
            case CLOSED, CANCELLED -> false;
        };
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
