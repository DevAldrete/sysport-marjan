package mx.marjan.api;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import java.util.Map;
import mx.marjan.shared.Database;

/** Liveness/readiness probe: the process is up and the database is reachable. */
@Controller("/api/health")
public class HealthController {

    @Get
    @Secured(SecurityRule.IS_ANONYMOUS)
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "database", Database.testConnection());
    }
}
