package mx.marjan.api.users;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.security.AuthService;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.security.Role;
import mx.marjan.security.User;
import mx.marjan.security.UserStatus;

/** Users, roles and password resets (FR-SEC-3), admin only. */
@Controller("/api/users")
@Secured(Permissions.SECURITY_USERS)
public class UserController {

    @Get
    public List<UserView> list(Authentication authentication) {
        return service(authentication).listUsers().stream().map(UserView::of).toList();
    }

    @Get("/roles")
    public List<Role> roles(Authentication authentication) {
        return service(authentication).roles();
    }

    @Post
    public HttpResponse<?> create(Authentication authentication, @Body UserWrite body) {
        return Responses.of(service(authentication).createUser(body.username(), body.password(),
                body.roleId() == null ? 0 : body.roleId(), body.employeeId(),
                body.status() == null ? UserStatus.ACTIVE : body.status()));
    }

    @Put("/{id}")
    public HttpResponse<?> update(Authentication authentication, long id, @Body UserWrite body) {
        return Responses.of(service(authentication).updateUser(id, body.username(),
                body.roleId() == null ? 0 : body.roleId(), body.employeeId(),
                body.status() == null ? UserStatus.ACTIVE : body.status()));
    }

    @Post("/{id}/password")
    public HttpResponse<?> resetPassword(Authentication authentication, long id,
            @Body PasswordReset body) {
        return Responses.of(service(authentication).resetPassword(id, body.password()));
    }

    @Delete("/{id}")
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).deleteUser(id));
    }

    private AuthService service(Authentication authentication) {
        Caller caller = Callers.forAuthentication(authentication);
        return new AuthService(caller);
    }

    public record UserWrite(String username, String password, Long roleId, Long employeeId,
            UserStatus status) {}

    public record PasswordReset(String password) {}

    /** A user without the password hash. */
    public record UserView(long id, Long employeeId, String username, long roleId, String roleName,
            UserStatus status) {
        static UserView of(User user) {
            return new UserView(user.id(), user.employeeId(), user.username(), user.roleId(),
                    user.roleName(), user.status());
        }
    }
}
