package mx.marjan.operators;

public enum EmployeeStatus {
    AVAILABLE("available", "Disponible"),
    ON_TRIP("on_trip", "En viaje"),
    RESTING("resting", "Descansando"),
    VACATION("vacation", "Vacaciones"),
    INCAPACITATED("incapacitated", "Incapacitado"),
    TERMINATED("terminated", "Dado de baja");

    private final String dbValue;
    private final String label;

    EmployeeStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    private static final EmployeeStatus[] MANUAL = {
        AVAILABLE, RESTING, VACATION, INCAPACITATED, TERMINATED
    };

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    /** Statuses a user may set by hand; 'on_trip' belongs to the trip lifecycle. */
    public static EmployeeStatus[] manualValues() {
        return MANUAL.clone();
    }

    public boolean isManual() {
        return this != ON_TRIP;
    }

    public static EmployeeStatus fromDb(String value) {
        if (value == null) {
            return AVAILABLE;
        }
        return switch (value.toLowerCase()) {
            case "on_trip" -> ON_TRIP;
            case "resting" -> RESTING;
            case "vacation" -> VACATION;
            case "incapacitated" -> INCAPACITATED;
            case "terminated" -> TERMINATED;
            default -> AVAILABLE;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
