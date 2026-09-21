package mx.marjan.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mx.marjan.shared.Database;

/** The only place with SQL for users, roles and permissions. */
public class UserRepository {

    private static final String BASE = """
            SELECT u.id, u.employee_id, u.username, u.password_hash,
                   u.role_id, r.name AS role_name, u.status
            FROM users u
            JOIN roles r ON r.id = u.role_id
            """;

    private User map(ResultSet rs) throws SQLException {
        Long employeeId = rs.getObject("employee_id", Long.class);
        return new User(
                rs.getLong("id"),
                employeeId,
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getLong("role_id"),
                rs.getString("role_name"),
                UserStatus.fromDb(rs.getString("status")));
    }

    public Optional<User> findByUsername(String username) {
        return Database.queryOne(BASE + " WHERE u.username = ?", this::map, username);
    }

    public Optional<User> findById(long id) {
        return Database.queryOne(BASE + " WHERE u.id = ?", this::map, id);
    }

    public List<User> list() {
        return Database.queryList(BASE + " ORDER BY u.username", this::map);
    }

    public List<Role> roles() {
        return Database.queryList("SELECT id, name FROM roles ORDER BY name",
                rs -> new Role(rs.getLong("id"), rs.getString("name")));
    }

    public Set<String> permissionsForRole(long roleId) {
        List<String> names = Database.queryList("""
                SELECT p.name
                FROM role_permissions rp
                JOIN permissions p ON p.id = rp.permission_id
                WHERE rp.role_id = ?
                ORDER BY p.name
                """, rs -> rs.getString("name"), roleId);
        return new LinkedHashSet<>(names);
    }

    public List<String> permissions() {
        return Database.queryList("SELECT name FROM permissions ORDER BY name", rs -> rs.getString("name"));
    }

    public long insert(String username, String passwordHash, long roleId, Long employeeId, UserStatus status) {
        return Database.insert("""
                INSERT INTO users (username, password_hash, role_id, employee_id, status)
                VALUES (?, ?, ?, ?, ?)
                """, username, passwordHash, roleId, employeeId, status.dbValue());
    }

    public void update(long id, String username, long roleId, Long employeeId, UserStatus status) {
        Database.update("""
                UPDATE users SET username = ?, role_id = ?, employee_id = ?, status = ?
                WHERE id = ?
                """, username, roleId, employeeId, status.dbValue(), id);
    }

    public void updatePassword(long id, String passwordHash) {
        Database.update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, id);
    }
}
