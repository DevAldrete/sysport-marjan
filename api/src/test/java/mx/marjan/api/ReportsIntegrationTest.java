package mx.marjan.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Reports are read-only, so this test only runs them and checks the CSV export. */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@EnabledIfEnvironmentVariable(named = "SYSPORT_IT", matches = "1")
class ReportsIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void reportsReturnRowsAndCsv() {
        String token = token("admin", "admin123");

        Map<String, Object> report = client.toBlocking().exchange(
                HttpRequest.GET("/api/reports/revenue?from=2026-01-01&to=2026-12-31").bearerAuth(token),
                Map.class).body();
        assertEquals("Ingresos por cliente", report.get("title"));
        assertNotNull(report.get("headers"));
        assertNotNull(report.get("rows"));

        String csv = client.toBlocking().exchange(
                HttpRequest.GET("/api/reports/receivables/csv").bearerAuth(token), String.class).body();
        assertNotNull(csv);
        assertFalse(csv.isBlank());
        assertEquals("cliente,factura,importe,pagado,saldo,vencimiento", csv.split("\n")[0].trim());
    }

    @Test
    void unknownReportIsRejected() {
        String token = token("admin", "admin123");
        io.micronaut.http.client.exceptions.HttpClientResponseException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        io.micronaut.http.client.exceptions.HttpClientResponseException.class,
                        () -> client.toBlocking().exchange(
                                HttpRequest.GET("/api/reports/nope").bearerAuth(token), Map.class));
        assertEquals(422, failure.getStatus().getCode());
    }

    private String token(String username, String password) {
        HttpResponse<Map> login = client.toBlocking().exchange(HttpRequest.POST("/api/auth/login",
                Map.of("username", username, "password", password)), Map.class);
        return (String) login.body().get("access_token");
    }
}
