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

    private static final VehicleStatus[] MANUAL = {
        AVAILABLE, MAINTENANCE, OUT_OF_SERVICE, DECOMMISSIONED
    };

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    /** Statuses a user may set by hand; 'assigned'/'on_trip' belong to the trip lifecycle. */
    public static VehicleStatus[] manualValues() {
        return MANUAL.clone();
    }

    public boolean isManual() {
        return this != ASSIGNED && this != ON_TRIP;
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
