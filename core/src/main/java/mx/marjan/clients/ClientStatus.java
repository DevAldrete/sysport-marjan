package mx.marjan.clients;

public enum ClientStatus {
    ACTIVE("active", "Activo"),
    INACTIVE("inactive", "Inactivo");

    private final String dbValue;
    private final String label;

    ClientStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static ClientStatus fromDb(String value) {
        return "inactive".equalsIgnoreCase(value) ? INACTIVE : ACTIVE;
    }

    @Override
    public String toString() {
        return label;
    }
}
