package mx.marjan.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mx.marjan.shared.Database;

/** The only place with SQL for users, roles and permissions (stored procedures). */
public class UserRepository {

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
        return Database.callOne("{call sp_user_by_username(?)}", this::map, username);
    }

    public Optional<User> findById(long id) {
        return Database.callOne("{call sp_user_by_id(?)}", this::map, id);
    }

    public List<User> list() {
        return Database.callList("{call sp_users_list()}", this::map);
    }

    public List<Role> roles() {
        return Database.callList("{call sp_roles_list()}",
                rs -> new Role(rs.getLong("id"), rs.getString("name")));
    }

    public Set<String> permissionsForRole(long roleId) {
        List<String> names = Database.callList("{call sp_role_permissions(?)}",
                rs -> rs.getString("name"), roleId);
        return new LinkedHashSet<>(names);
    }

    public long insert(String username, String passwordHash, long roleId, Long employeeId,
            UserStatus status) {
        Object[] out = Database.call("{call sp_user_insert(?,?,?,?,?,?)}",
                new int[] { Types.BIGINT }, username, passwordHash, roleId, employeeId,
                status.dbValue());
        return Database.asLong(out[0]);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_user_delete(?)}", id);
    }

    public void update(long id, String username, long roleId, Long employeeId, UserStatus status) {
        Database.callNoOut("{call sp_user_update(?,?,?,?,?)}",
                id, username, roleId, employeeId, status.dbValue());
    }

    public void updatePassword(long id, String passwordHash) {
        Database.callNoOut("{call sp_user_update_password(?,?)}", id, passwordHash);
    }
}
