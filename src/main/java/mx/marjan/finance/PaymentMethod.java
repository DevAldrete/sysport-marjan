package mx.marjan.finance;

public enum PaymentMethod {
    CASH("cash", "Efectivo"),
    TRANSFER("transfer", "Transferencia"),
    CHECK("check", "Cheque"),
    CARD("card", "Tarjeta"),
    OTHER("other", "Otro");

    private final String dbValue;
    private final String label;

    PaymentMethod(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static PaymentMethod fromDb(String value) {
        if (value == null) {
            return OTHER;
        }
        for (PaymentMethod method : values()) {
            if (method.dbValue.equalsIgnoreCase(value)) {
                return method;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
