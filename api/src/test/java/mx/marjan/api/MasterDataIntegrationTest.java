package mx.marjan.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Master data (routes, operators, vehicles, fuel) against the real database. */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@EnabledIfEnvironmentVariable(named = "SYSPORT_IT", matches = "1")
class MasterDataIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void routeLifecycle() {
        String token = token("admin", "admin123");
        String suffix = unique();

        Map<String, Object> route = Map.of(
                "origin", "Origen " + suffix,
                "destination", "Destino " + suffix,
                "estimatedKm", 123.4,
                "description", "prueba");
        Map<String, Object> created = post("/api/routes", route, token);

        long id = ((Number) created.get("id")).longValue();
        List<?> found = exchange(HttpRequest.GET("/api/routes?term=" + suffix).bearerAuth(token), List.class).body();
        assertFalse(found.isEmpty(), "the created route is searchable");

        assertEquals(HttpStatus.NO_CONTENT,
                delete("/api/routes/" + id, token).getStatus());
    }

    @Test
    void operatorLifecycleWithLicense() {
        String token = token("admin", "admin123");
        String suffix = unique();

        Map<String, Object> license = Map.of(
                "licenseNumber", "LIC-" + suffix,
                "licenseType", "Federal C",
                "issueDate", "2024-01-01",
                "expirationDate", "2030-01-01");
        Map<String, Object> operator = new HashMap<>();
        operator.put("name", "Operador " + suffix);
        operator.put("address", "Calle " + suffix);
        operator.put("phone", "55" + suffix);
        operator.put("email", "op" + suffix + "@test.mx");
        operator.put("rfc", "TST" + suffix.substring(0, 6) + "AB1");
        operator.put("curp", "TSTA" + suffix.substring(0, 6) + "HDFRSN01");
        operator.put("emergencyContactName", "Contacto");
        operator.put("emergencyContactPhone", "55" + suffix);
        operator.put("license", license);
        operator.put("status", "available");

        Map<String, Object> created = post("/api/operators", operator, token);
        long id = ((Number) created.get("id")).longValue();
        assertNotNull(created.get("license"));

        Map<String, Object> statusChange = Map.of("status", "vacation");
        HttpResponse<?> moved = client.toBlocking().exchange(
                HttpRequest.POST("/api/operators/" + id + "/status", statusChange).bearerAuth(token),
                String.class);
        assertEquals(HttpStatus.NO_CONTENT, moved.getStatus());

        Map<String, Object> reloaded = exchange(
                HttpRequest.GET("/api/operators/" + id).bearerAuth(token), Map.class).body();
        assertEquals("vacation", reloaded.get("status"));

        assertEquals(HttpStatus.NO_CONTENT, delete("/api/operators/" + id, token).getStatus());
    }

    @Test
    void vehicleMaintenanceAndFuelLifecycle() {
        String token = token("admin", "admin123");
        String suffix = unique();

        Map<String, Object> vehicle = Map.of(
                "internalCode", "U" + suffix.substring(0, 5),
                "plates", "QWE-" + suffix.substring(0, 3) + "-Z",
                "brand", "Isuzu",
                "model", "ELF",
                "year", 2020,
                "serialNumber", "SER" + suffix,
                "vehicleType", "Camion 3.5t",
                "loadCapacity", 3500.0,
                "mileage", 1000.0,
                "status", "available");
        Map<String, Object> createdVehicle = post("/api/vehicles", vehicle, token);
        long vehicleId = ((Number) createdVehicle.get("id")).longValue();

        Map<String, Object> maintenance = Map.of(
                "maintenanceDate", "2026-01-05",
                "odometerReading", 5000.0,
                "type", "preventive",
                "workPerformed", "Servicio",
                "provider", "Taller",
                "cost", 1500.0);
        post("/api/vehicles/" + vehicleId + "/maintenance", maintenance, token);

        List<?> history = exchange(
                HttpRequest.GET("/api/vehicles/" + vehicleId + "/maintenance").bearerAuth(token), List.class)
                .body();
        assertFalse(history.isEmpty(), "maintenance was recorded");

        Map<String, Object> fuel = Map.of(
                "vehicleId", vehicleId,
                "fuelStation", "Pemex",
                "loadDate", "2026-01-06T08:00:00",
                "liters", 10.0,
                "pricePerLiter", 25.5,
                "amount", 255.0,
                "odometerReading", 6000.0);
        post("/api/fuel", fuel, token);
        List<?> loads = exchange(HttpRequest.GET("/api/fuel").bearerAuth(token), List.class).body();
        assertFalse(loads.isEmpty(), "fuel load was recorded");

        // Clean up: fuel, maintenance, then the vehicle itself.
        long fuelId = fuelIdFor(vehicleId, token);
        if (fuelId > 0) {
            assertEquals(HttpStatus.NO_CONTENT, delete("/api/fuel/" + fuelId, token).getStatus());
        }
        long maintenanceId = ((Number) ((Map<?, ?>) history.get(0)).get("id")).longValue();
        assertEquals(HttpStatus.NO_CONTENT,
                delete("/api/vehicles/maintenance/" + maintenanceId, token).getStatus());
        assertEquals(HttpStatus.NO_CONTENT, delete("/api/vehicles/" + vehicleId, token).getStatus());
    }

    private long fuelIdFor(long vehicleId, String token) {
        List<?> loads = exchange(HttpRequest.GET("/api/fuel").bearerAuth(token), List.class).body();
        for (Object item : loads) {
            Map<?, ?> load = (Map<?, ?>) item;
            if (((Number) load.get("vehicleId")).longValue() == vehicleId) {
                return ((Number) load.get("id")).longValue();
            }
        }
        return 0;
    }

    @Test
    void viewerCannotWriteMasterData() {
        String token = token("viewer", "admin123");
        HttpClientResponseException forbidden = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/api/routes",
                        Map.of("origin", "X", "destination", "Y")).bearerAuth(token), Map.class));
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
            String body = failure.getResponse().getBody(String.class).orElse("");
            throw new AssertionError(request.getMethod() + " " + request.getPath() + " -> " + body,
                    failure);
        }
    }

    private String unique() {
        return String.format("%08d", System.nanoTime() % 100_000_000);
    }
}
