package mx.marjan.clients;

public enum ClientType {
    OCCASIONAL("occasional", "Ocasional"),
    FREQUENT("frequent", "Frecuente");

    private final String dbValue;
    private final String label;

    ClientType(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static ClientType fromDb(String value) {
        return "frequent".equalsIgnoreCase(value) ? FREQUENT : OCCASIONAL;
    }

    @Override
    public String toString() {
        return label;
    }
}
