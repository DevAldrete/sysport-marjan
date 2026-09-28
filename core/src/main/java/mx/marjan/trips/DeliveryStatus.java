package mx.marjan.trips;

public enum DeliveryStatus {
    PENDING_DOCUMENTS("pending_documents", "Documentos pendientes"),
    COMPLETE("complete", "Completa");

    private final String dbValue;
    private final String label;

    DeliveryStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static DeliveryStatus fromDb(String value) {
        return "complete".equalsIgnoreCase(value) ? COMPLETE : PENDING_DOCUMENTS;
    }

    @Override
    public String toString() {
        return label;
    }
}
