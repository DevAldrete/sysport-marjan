package mx.marjan.api.trips;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.security.Permissions;
import mx.marjan.trips.Incident;
import mx.marjan.trips.IncidentService;
import mx.marjan.trips.IncidentType;

/** Trip incidents (FR-TRP-5). */
@Controller("/api/incidents")
public class IncidentController {

    @Get
    @Secured(Permissions.INCIDENTS_READ)
    public List<Incident> list(Authentication authentication, @QueryValue long tripId) {
        return service(authentication).listByTrip(tripId);
    }

    @Post
    @Secured(Permissions.INCIDENTS_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body Incident incident) {
        Incident record = new Incident(0, incident.tripId(), null, incident.incidentDate(),
                incident.incidentTime(), incident.location(),
                incident.type() == null ? IncidentType.OTHER : incident.type(),
                incident.description(), incident.actionsTaken());
        return Responses.of(service(authentication).register(record));
    }

    @Delete("/{id}")
    @Secured(Permissions.INCIDENTS_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private IncidentService service(Authentication authentication) {
        return new IncidentService(Callers.forAuthentication(authentication));
    }
}
