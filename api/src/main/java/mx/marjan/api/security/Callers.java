package mx.marjan.api.security;

import io.micronaut.security.authentication.Authentication;
import mx.marjan.security.AuthService;
import mx.marjan.security.Caller;

/** Builds a {@link Caller} for the authenticated user of the current request. */
public final class Callers {

    private Callers() {}

    public static Caller forAuthentication(Authentication authentication) {
        return new Caller() {

            @Override
            public boolean has(String permission) {
                return authentication.getRoles().contains(permission);
            }

            @Override
            public long userId() {
                Object id = authentication.getAttributes().get("userId");
                if (id instanceof Number number) {
                    return number.longValue();
                }
                return new AuthService(Caller.NONE).byUsername(authentication.getName())
                        .map(mx.marjan.security.CurrentUser::id).orElse(0L);
            }
        };
    }
}
