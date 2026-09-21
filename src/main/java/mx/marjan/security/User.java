package mx.marjan.security;

public record User(
        long id,
        Long employeeId,
        String username,
        String passwordHash,
        long roleId,
        String roleName,
        UserStatus status) {

    public User withStatus(UserStatus newStatus) {
        return new User(id, employeeId, username, passwordHash, roleId, roleName, newStatus);
    }
}
