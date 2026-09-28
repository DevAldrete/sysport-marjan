package mx.marjan.requests;

/** How a package is counted (kept in sync with the request_packages CHECK). */
public enum PackageUnit {
    BOX("caja", "Caja"),
    PALLET("paleta", "Paleta"),
    BAG("saco", "Saco"),
    BUNDLE("bulto", "Bulto"),
    PIECE("pieza", "Pieza"),
    CONTAINER("contenedor", "Contenedor"),
    OTHER("otro", "Otro");

    private final String dbValue;
    private final String label;

    PackageUnit(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static PackageUnit fromDb(String value) {
        if (value == null) {
            return OTHER;
        }
        for (PackageUnit unit : values()) {
            if (unit.dbValue.equalsIgnoreCase(value)) {
                return unit;
            }
        }
        return OTHER;
    }

    @Override
    public String toString() {
        return label;
    }
}
