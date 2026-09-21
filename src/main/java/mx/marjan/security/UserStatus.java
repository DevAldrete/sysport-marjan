package mx.marjan.security;

public enum UserStatus {
    ACTIVE,
    DISABLED;

    public static UserStatus fromDb(String value) {
        return "disabled".equalsIgnoreCase(value) ? DISABLED : ACTIVE;
    }

    public String dbValue() {
        return name().toLowerCase();
    }
}
