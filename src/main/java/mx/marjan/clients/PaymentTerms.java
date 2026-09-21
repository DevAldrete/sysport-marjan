package mx.marjan.clients;

public enum PaymentTerms {
    CASH("cash", "Contado"),
    CREDIT("credit", "Credito");

    private final String dbValue;
    private final String label;

    PaymentTerms(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static PaymentTerms fromDb(String value) {
        return "credit".equalsIgnoreCase(value) ? CREDIT : CASH;
    }

    @Override
    public String toString() {
        return label;
    }
}
