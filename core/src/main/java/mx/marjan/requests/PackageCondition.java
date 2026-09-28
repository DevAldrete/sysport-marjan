package mx.marjan.requests;

/** Condition of a package when it is delivered (null means not received yet). */
public enum PackageCondition {
    OK("ok", "Completo"),
    SHORTAGE("shortage", "Faltante"),
    DAMAGED("damaged", "Danado"),
    MISSING("missing", "No llego");

    private final String dbValue;
    private final String label;

    PackageCondition(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    /** Null is meaningful here: the package has no receipt yet. */
    public static PackageCondition fromDb(String value) {
        if (value == null) {
            return null;
        }
        for (PackageCondition condition : values()) {
            if (condition.dbValue.equalsIgnoreCase(value)) {
                return condition;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return label;
    }
}
