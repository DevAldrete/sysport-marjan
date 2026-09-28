package mx.marjan.finance;

public enum InvoiceStatus {
    PENDING("pending", "Pendiente"),
    PAID("paid", "Pagada"),
    OVERDUE("overdue", "Vencida"),
    CANCELLED("cancelled", "Cancelada");

    private final String dbValue;
    private final String label;

    InvoiceStatus(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static InvoiceStatus fromDb(String value) {
        if (value == null) {
            return PENDING;
        }
        return switch (value.toLowerCase()) {
            case "paid" -> PAID;
            case "overdue" -> OVERDUE;
            case "cancelled" -> CANCELLED;
            default -> PENDING;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
