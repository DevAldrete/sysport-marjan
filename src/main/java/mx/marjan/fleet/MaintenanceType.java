package mx.marjan.fleet;

public enum MaintenanceType {
    PREVENTIVE("preventive", "Preventivo"),
    CORRECTIVE("corrective", "Correctivo");

    private final String dbValue;
    private final String label;

    MaintenanceType(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static MaintenanceType fromDb(String value) {
        return "corrective".equalsIgnoreCase(value) ? CORRECTIVE : PREVENTIVE;
    }

    @Override
    public String toString() {
        return label;
    }
}
