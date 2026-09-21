package mx.marjan.fleet;

public enum VehicleStatus {
    AVAILABLE("available", "Disponible"),
    ASSIGNED("assigned", "Asignada"),
    ON_TRIP("on_trip", "En viaje"),
    MAINTENANCE("maintenance", "Mantenimiento"),
    OUT_OF_SERVICE("out_of_service", "Fuera de servicio"),
    DECOMMISSIONED("decommissioned", "Dada de baja");

    private final String dbValue;
    private final String label;

    VehicleStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    /** BR-07 / BR-11: only these statuses may be assigned to a trip. */
    public boolean isAssignable() {
        return this == AVAILABLE;
    }

    public static VehicleStatus fromDb(String value) {
        if (value == null) {
            return AVAILABLE;
        }
        return switch (value.toLowerCase()) {
            case "assigned" -> ASSIGNED;
            case "on_trip" -> ON_TRIP;
            case "maintenance" -> MAINTENANCE;
            case "out_of_service" -> OUT_OF_SERVICE;
            case "decommissioned" -> DECOMMISSIONED;
            default -> AVAILABLE;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
