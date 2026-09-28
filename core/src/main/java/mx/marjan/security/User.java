package mx.marjan.security;

public record User(
        long id,
        Long employeeId,
        String username,
        String passwordHash,
        long roleId,
        String roleName,
        UserStatus status) {

    /** Never includes the password hash. */
    @Override
    public String toString() {
        return username + " (" + roleName + ")";
    }
}
