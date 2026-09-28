package mx.marjan.operators;

/**
 * Licence kinds offered by the operator form. The values mirror the
 * {@code licenses.license_type} CHECK constraint, so the dropdown can only
 * produce a permitted value.
 */
public enum LicenseType {
    FEDERAL_A("Federal A"),
    FEDERAL_B("Federal B"),
    FEDERAL_C("Federal C"),
    FEDERAL_D("Federal D"),
    FEDERAL_E("Federal E"),
    STATE("Estatal"),
    OTHER("Otro");

    private final String label;

    LicenseType(String label) {
        this.label = label;
    }

    public String dbValue() {
        return label;
    }

    public String label() {
        return label;
    }

    /** The matching type, or {@link #OTHER} for an unknown value; null when blank. */
    public static LicenseType fromDb(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (LicenseType type : values()) {
            if (type.label.equalsIgnoreCase(value.trim())) {
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
