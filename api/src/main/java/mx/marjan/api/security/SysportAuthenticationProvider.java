package mx.marjan.api.security;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import mx.marjan.security.AuthService;
import mx.marjan.security.CurrentUser;
import mx.marjan.shared.Result;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * Verifies credentials with the core {@link AuthService} (BCrypt, same messages
 * as the desktop) and turns the identity into a JWT whose roles are the user's
 * permission strings, so {@code @Secured("clients.read")} works.
 */
@Singleton
public class SysportAuthenticationProvider implements AuthenticationProvider<HttpRequest<?>> {

    private final AuthService authService = new AuthService();

    @Override
    public Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> request,
            AuthenticationRequest<?, ?> authenticationRequest) {
        Object identity = authenticationRequest.getIdentity();
        Object secret = authenticationRequest.getSecret();
        Result<CurrentUser> result = authService.login(asText(identity), asText(secret));
        if (result.isErr()) {
            return Flux.just(AuthenticationResponse.failure(firstProblem(result)));
        }
        CurrentUser user = result.value();
        return Flux.just(AuthenticationResponse.success(
                user.username(),
                List.copyOf(user.permissions()),
                Map.of("userId", user.id(), "roleName", user.roleName())));
    }

    private String asText(Object value) {
        return value == null ? null : value.toString();
    }

    private String firstProblem(Result<CurrentUser> result) {
        return result.problems().isEmpty() ? "Usuario o contrasena incorrectos" : result.problems().get(0);
    }
}
