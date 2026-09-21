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

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
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

    /** BR-09: only available operators can be assigned. */
    public boolean isAssignable() {
        return this == AVAILABLE;
    }

    @Override
    public String toString() {
        return label;
    }
}
