package mx.marjan.finance;

public enum ExpenseType {
    TOLLS("tolls", "Casetas"),
    FOOD("food", "Alimentos"),
    PARKING("parking", "Estacionamiento"),
    LODGING("lodging", "Hospedaje"),
    REPAIRS("repairs", "Reparaciones"),
    HANDLING("handling", "Maniobras"),
    PERMITS("permits", "Permisos"),
    OTHER("other", "Otro");

    private final String dbValue;
    private final String label;

    ExpenseType(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static ExpenseType fromDb(String value) {
        if (value == null) {
            return OTHER;
        }
        for (ExpenseType type : values()) {
            if (type.dbValue.equalsIgnoreCase(value)) {
                return type;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
