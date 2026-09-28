package mx.marjan.api.auth;

import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.rules.SecurityRule;
import java.util.List;
import mx.marjan.api.error.ApiProblemException;
import mx.marjan.api.security.Callers;
import mx.marjan.security.AuthService;
import mx.marjan.security.Caller;
import mx.marjan.security.CurrentUser;
import mx.marjan.shared.Result;

/**
 * Session helpers around the security module's login/refresh/logout endpoints:
 * who am I, and changing my own password. Login and refresh are handled by
 * Micronaut Security (paths configured in application.yml).
 */
@Controller("/api/auth")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class AuthController {

    @Get("/me")
    public Me me(Authentication authentication) {
        CurrentUser user = new AuthService(Caller.NONE).byUsername(authentication.getName())
                .orElseThrow(() -> new ApiProblemException(List.of("Usuario no encontrado")));
        return new Me(user.id(), user.username(), user.roleName(), List.copyOf(user.permissions()));
    }

    @Post("/password")
    public void changePassword(Authentication authentication, @Body PasswordChange body) {
        Result<Void> result = new AuthService(Callers.forAuthentication(authentication))
                .changeOwnPassword(body.currentPassword(), body.newPassword());
        if (result.isErr()) {
            throw new ApiProblemException(result.problems());
        }
    }

    public record Me(long userId, String username, String roleName, List<String> permissions) {}

    public record PasswordChange(String currentPassword, String newPassword) {}
}
