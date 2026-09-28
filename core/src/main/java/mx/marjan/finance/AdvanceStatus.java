package mx.marjan.finance;

public enum AdvanceStatus {
    PENDING("pending", "Pendiente"),
    SETTLED("settled", "Comprobado");

    private final String dbValue;
    private final String label;

    AdvanceStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static AdvanceStatus fromDb(String value) {
        return "settled".equalsIgnoreCase(value) ? SETTLED : PENDING;
    }

    @Override
    public String toString() {
        return label;
    }
}
