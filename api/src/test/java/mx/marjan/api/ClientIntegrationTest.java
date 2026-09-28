package mx.marjan.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Exercises the dashboard and clients endpoints against the real database,
 * including permission enforcement and the 422 problem mapping.
 * {@code SYSPORT_IT=1 mvn -pl api test}.
 */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@EnabledIfEnvironmentVariable(named = "SYSPORT_IT", matches = "1")
class ClientIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void dashboardAndClientSearchWork() {
        String token = token("admin", "admin123");

        HttpResponse<Map> dashboard = client.toBlocking()
                .exchange(HttpRequest.GET("/api/dashboard").bearerAuth(token), Map.class);
        assertEquals(HttpStatus.OK, dashboard.getStatus());
        assertNotNull(dashboard.body().get("pendingAssignments"));

        HttpResponse<List> clients = client.toBlocking()
                .exchange(HttpRequest.GET("/api/clients").bearerAuth(token), List.class);
        assertFalse(clients.body().isEmpty(), "seed has clients");

        HttpResponse<List> filtered = client.toBlocking()
                .exchange(HttpRequest.GET("/api/clients?term=Distri").bearerAuth(token), List.class);
        assertFalse(filtered.body().isEmpty(), "search filters by term");
    }

    @Test
    void createValidatesAndDeleteCleansUp() {
        String token = token("admin", "admin123");

        HttpClientResponseException invalid = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/api/clients",
                        Map.of("name", "")).bearerAuth(token), Map.class));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, invalid.getStatus());

        HttpResponse<Map> created = client.toBlocking().exchange(HttpRequest.POST("/api/clients", Map.of(
                "name", "API Test SA de CV",
                "rfc", "ABC010101ZZ1",
                "phone", "5550000000",
                "email", "contacto@apitest.mx",
                "clientType", "occasional",
                "paymentTerms", "cash",
                "creditLimit", 0,
                "creditDays", 0,
                "status", "active")).bearerAuth(token), Map.class);
        assertEquals(HttpStatus.OK, created.getStatus());
        Number id = (Number) created.body().get("id");
        assertNotNull(id);

        HttpResponse<?> deleted = client.toBlocking().exchange(
                HttpRequest.DELETE("/api/clients/" + id.longValue()).bearerAuth(token), String.class);
        assertEquals(HttpStatus.NO_CONTENT, deleted.getStatus());
    }

    @Test
    void viewerCannotWriteClients() {
        String token = token("viewer", "admin123");

        HttpClientResponseException forbidden = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/api/clients",
                        Map.of("name", "Nope")).bearerAuth(token), Map.class));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
    }

    private String token(String username, String password) {
        HttpResponse<Map> login = client.toBlocking().exchange(HttpRequest.POST("/api/auth/login",
                Map.of("username", username, "password", password)), Map.class);
        return (String) login.body().get("access_token");
    }
}
