package mx.marjan.trips;

public enum IncidentType {
    ACCIDENT("accident", "Accidente"),
    MECHANICAL_FAILURE("mechanical_failure", "Falla mecanica"),
    DELAY("delay", "Retraso"),
    ROAD_CLOSURE("road_closure", "Cierre carretero"),
    CARGO_DAMAGE("cargo_damage", "Dano a la mercancia"),
    DOCUMENTATION_ISSUE("documentation_issue", "Problema de documentacion"),
    OTHER("other", "Otro");

    private final String dbValue;
    private final String label;

    IncidentType(String dbValue, String label) {
        this.dbValue = dbValue;
        this.label = label;
    }

    public String dbValue() {
        return dbValue;
    }

    public String label() {
        return label;
    }

    public static IncidentType fromDb(String value) {
        if (value == null) {
            return OTHER;
        }
        return switch (value.toLowerCase()) {
            case "accident" -> ACCIDENT;
            case "mechanical_failure" -> MECHANICAL_FAILURE;
            case "delay" -> DELAY;
            case "road_closure" -> ROAD_CLOSURE;
            case "cargo_damage" -> CARGO_DAMAGE;
            case "documentation_issue" -> DOCUMENTATION_ISSUE;
            default -> OTHER;
        };
    }

    @Override
    public String toString() {
        return label;
    }
}
