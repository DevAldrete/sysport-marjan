package mx.marjan.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Service request create/read/delete with packages against the real database.
 * Assignment is exercised manually (it consumes a vehicle and operator, which
 * would perturb the seeded data used by other tests).
 */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@EnabledIfEnvironmentVariable(named = "SYSPORT_IT", matches = "1")
class RequestIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void createRequestWithPackagesThenDelete() {
        String token = token("admin", "admin123");
        String suffix = String.format("%06d", System.nanoTime() % 1_000_000);

        Map<String, Object> body = Map.of(
                "clientId", 2,
                "routeId", 4,
                "cargoDescription", "Prueba " + suffix,
                "estimatedWeight", 1000,
                "pickupScheduled", "2027-05-05T08:00",
                "deliveryScheduled", "2027-05-06T18:00",
                "requiresDocuments", false,
                "notes", "test",
                "packages", List.of(
                        Map.of("id", 0, "description", "Cajas", "quantity", 10, "unit", "caja",
                                "unitWeight", 20),
                        Map.of("id", 0, "description", "Piezas", "quantity", 5, "unit", "pieza",
                                "unitWeight", 30)));

        Map<String, Object> created = post("/api/requests", body, token);
        long id = ((Number) created.get("id")).longValue();
        assertTrue(((String) created.get("folio")).matches("SR-\\d{4}-\\d{6}"));
        assertEquals(2, ((Number) created.get("packageCount")).intValue());

        Map<String, Object> reloaded = exchange(
                HttpRequest.GET("/api/requests/" + id).bearerAuth(token), Map.class).body();
        assertEquals("requested", reloaded.get("status"));
        assertEquals(0, new java.math.BigDecimal("350.0")
                .compareTo(new java.math.BigDecimal(reloaded.get("packageWeight").toString())));

        List<?> packages = exchange(
                HttpRequest.GET("/api/requests/" + id + "/packages").bearerAuth(token), List.class).body();
        assertEquals(2, packages.size());

        List<?> found = exchange(
                HttpRequest.GET("/api/requests?folio=" + created.get("folio")).bearerAuth(token), List.class)
                .body();
        assertFalse(found.isEmpty(), "the request is searchable by folio");

        assertEquals(HttpStatus.NO_CONTENT, delete("/api/requests/" + id, token).getStatus());
    }

    @Test
    void viewerCannotCreateRequests() {
        String token = token("viewer", "admin123");
        HttpClientResponseException forbidden = org.junit.jupiter.api.Assertions.assertThrows(
                HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/api/requests",
                        Map.of("clientId", 2, "routeId", 4, "cargoDescription", "x")).bearerAuth(token),
                        Map.class));
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatus());
    }

    private String token(String username, String password) {
        HttpResponse<Map> login = client.toBlocking().exchange(HttpRequest.POST("/api/auth/login",
                Map.of("username", username, "password", password)), Map.class);
        return (String) login.body().get("access_token");
    }

    private Map<String, Object> post(String path, Object body, String token) {
        return exchange(HttpRequest.POST(path, body).bearerAuth(token), Map.class).body();
    }

    private HttpResponse<?> delete(String path, String token) {
        return exchange(HttpRequest.DELETE(path).bearerAuth(token), String.class);
    }

    private <T> HttpResponse<T> exchange(HttpRequest<?> request, Class<T> type) {
        try {
            return client.toBlocking().exchange(request, type);
        } catch (HttpClientResponseException failure) {
            throw new AssertionError(request.getMethod() + " " + request.getPath() + " -> "
                    + failure.getResponse().getBody(String.class).orElse(""), failure);
        }
    }
}
