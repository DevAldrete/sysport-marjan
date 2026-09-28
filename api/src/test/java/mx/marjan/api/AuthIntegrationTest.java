package mx.marjan.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.context.annotation.Property;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Boots the real API against the docker MySQL and exercises the JWT flow.
 * Opt-in so {@code mvn test} stays fast and database-free:
 * {@code SYSPORT_IT=1 mvn -pl api test}.
 */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@EnabledIfEnvironmentVariable(named = "SYSPORT_IT", matches = "1")
class AuthIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void loginReturnsTokensAndMeWithPermissions() {
        HttpResponse<Map> login = client.toBlocking().exchange(HttpRequest.POST("/api/auth/login",
                Map.of("username", "admin", "password", "admin123")), Map.class);

        assertEquals(HttpStatus.OK, login.getStatus());
        String accessToken = (String) login.body().get("access_token");
        assertNotNull(accessToken, "login returns an access token");
        assertNotNull(login.body().get("refresh_token"), "login returns a refresh token");

        HttpResponse<Map> me = client.toBlocking().exchange(
                HttpRequest.GET("/api/auth/me").bearerAuth(accessToken), Map.class);

        assertEquals("admin", me.body().get("username"));
        assertEquals("admin", me.body().get("roleName"));
        assertNotNull(me.body().get("permissions"));
    }

    @Test
    void meWithoutTokenIsUnauthorized() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.GET("/api/auth/me"), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatus());
    }

    @Test
    void badCredentialsAreRejected() {
        HttpClientResponseException failure = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/api/auth/login",
                        Map.of("username", "admin", "password", "wrong")), Map.class));
        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatus());
    }
}
