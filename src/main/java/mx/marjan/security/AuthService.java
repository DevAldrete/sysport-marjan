package mx.marjan.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Result;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Authentication and user administration.
 * Passwords are only ever stored (and logged) as BCrypt hashes (BR-24).
 */
public class AuthService {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository users = new UserRepository();

    public Result<CurrentUser> login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return Result.err("Usuario y contrasena son obligatorios");
        }
        Optional<User> found = users.findByUsername(username.trim());
        if (found.isEmpty()) {
            return Result.err("Usuario o contrasena incorrectos");
        }
        User user = found.get();
        if (user.status() != UserStatus.ACTIVE) {
            return Result.err("El usuario esta deshabilitado");
        }
        if (!BCrypt.checkpw(password, user.passwordHash())) {
            return Result.err("Usuario o contrasena incorrectos");
        }
        return Result.ok(new CurrentUser(
                user.id(), user.username(), user.roleName(), users.permissionsForRole(user.roleId())));
    }

    public List<User> listUsers() {
        return users.list();
    }

    public List<Role> roles() {
        return users.roles();
    }

    public List<String> permissions() {
        return users.permissions();
    }

    public Result<Void> createUser(String username, String password, long roleId, Long employeeId, UserStatus status) {
        Result<Void> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        List<String> problems = new ArrayList<>();
        if (username == null || username.isBlank()) {
            problems.add("El nombre de usuario es obligatorio");
        } else if (!mx.marjan.shared.Validators.isValidUsername(username)) {
            problems.add("El usuario solo admite letras, numeros y . _ - (3 a 50 caracteres)");
        } else if (users.findByUsername(username.trim()).isPresent()) {
            problems.add("Ya existe un usuario con ese nombre");
        }
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            problems.add("La contrasena debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        String name = username.trim();
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        mx.marjan.shared.Database.inTransaction(connection -> {
            users.insert(connection, name, hash, roleId, employeeId, status);
            return null;
        });
        return Result.ok(null);
    }

    public Result<Void> deleteUser(long id) {
        Result<Void> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        if (id == Session.userId()) {
            return Result.err("No puede eliminar su propio usuario");
        }
        users.delete(id);
        return Result.ok(null);
    }

    public Result<Void> updateUser(long id, String username, long roleId, Long employeeId, UserStatus status) {
        Result<Void> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        List<String> problems = new ArrayList<>();
        if (username == null || username.isBlank()) {
            problems.add("El nombre de usuario es obligatorio");
        }
        Optional<User> byName = username == null ? Optional.empty() : users.findByUsername(username.trim());
        if (byName.isPresent() && byName.get().id() != id) {
            problems.add("Ya existe un usuario con ese nombre");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        users.update(id, username.trim(), roleId, employeeId, status);
        return Result.ok(null);
    }

    public Result<Void> resetPassword(long userId, String newPassword) {
        Result<Void> denied = requireAdmin();
        if (denied != null) {
            return denied;
        }
        if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
            return Result.err("La contrasena debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        users.updatePassword(userId, BCrypt.hashpw(newPassword, BCrypt.gensalt(12)));
        return Result.ok(null);
    }

    public Result<Void> changeOwnPassword(String currentPassword, String newPassword) {
        long userId = Session.userId();
        return users.findById(userId).map(user -> {
            if (!BCrypt.checkpw(currentPassword, user.passwordHash())) {
                return Result.<Void>err("La contrasena actual no es correcta");
            }
            if (newPassword == null || newPassword.length() < MIN_PASSWORD_LENGTH) {
                return Result.<Void>err("La contrasena debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
            }
            users.updatePassword(userId, BCrypt.hashpw(newPassword, BCrypt.gensalt(12)));
            return Result.<Void>ok(null);
        }).orElse(Result.err("Usuario no encontrado"));
    }

    private Result<Void> requireAdmin() {
        if (!Session.has(Permissions.SECURITY_USERS)) {
            return Result.err("No tiene permiso para administrar usuarios");
        }
        return null;
    }
}
