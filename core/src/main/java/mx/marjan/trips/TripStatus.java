package mx.marjan.trips;

public enum TripStatus {
    SCHEDULED("scheduled", "Programado"),
    IN_TRANSIT("in_transit", "En transito"),
    COMPLETED("completed", "Completado"),
    CANCELLED("cancelled", "Cancelado");

    private final String dbValue;
    private final String label;

    TripStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    /** A trip occupies a resource only while scheduled or in transit (BR-05/06). */
    public boolean isActive() {
        return this == SCHEDULED || this == IN_TRANSIT;
    }

    public static TripStatus fromDb(String value) {
        if (value == null) {
            return SCHEDULED;
        }
        return switch (value.toLowerCase()) {
            case "in_transit" -> IN_TRANSIT;
            case "completed" -> COMPLETED;
            case "cancelled" -> CANCELLED;
            default -> SCHEDULED;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
