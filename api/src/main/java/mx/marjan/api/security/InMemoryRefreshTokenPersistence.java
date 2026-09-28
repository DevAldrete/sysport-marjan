package mx.marjan.api.security;

import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.event.RefreshTokenGeneratedEvent;
import io.micronaut.security.token.refresh.RefreshTokenPersistence;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

/**
 * Keeps issued refresh tokens in memory so the refresh endpoint can validate
 * them. Tokens are lost on restart; a persistent store (and revocation) is a
 * later step, kept out of the schema for now.
 */
@Singleton
public class InMemoryRefreshTokenPersistence implements RefreshTokenPersistence {

    private final Map<String, Authentication> issued = new ConcurrentHashMap<>();

    @Override
    public void persistToken(RefreshTokenGeneratedEvent event) {
        issued.put(event.getRefreshToken(), event.getAuthentication());
    }

    @Override
    public Publisher<Authentication> getAuthentication(String refreshToken) {
        Authentication authentication = issued.get(refreshToken);
        return authentication == null ? Flux.empty() : Flux.just(authentication);
    }
}
