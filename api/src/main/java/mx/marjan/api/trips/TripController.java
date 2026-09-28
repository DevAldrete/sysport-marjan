package mx.marjan.api.trips;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.math.BigDecimal;
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.security.Permissions;
import mx.marjan.trips.Trip;
import mx.marjan.trips.TripService;
import mx.marjan.trips.TripStatus;

/** Trip execution: departure, arrival, reassignment and cancel (FR-TRP-3/4). */
@Controller("/api/trips")
public class TripController {

    @Get
    @Secured(Permissions.TRIPS_READ)
    public List<Trip> search(Authentication authentication, @Nullable @QueryValue String term,
            @Nullable @QueryValue String status) {
        TripStatus tripStatus = status == null || status.isBlank() ? null : TripStatus.fromDb(status);
        return service(authentication).search(term, tripStatus);
    }

    @Get("/{id}")
    @Secured(Permissions.TRIPS_READ)
    public HttpResponse<?> find(Authentication authentication, long id) {
        return service(authentication).find(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post("/{id}/depart")
    @Secured(Permissions.TRIPS_WRITE)
    public HttpResponse<?> depart(Authentication authentication, long id) {
        return Responses.of(service(authentication).depart(id));
    }

    @Post("/{id}/arrive")
    @Secured(Permissions.TRIPS_WRITE)
    public HttpResponse<?> arrive(Authentication authentication, long id, @Body ArriveWrite body) {
        return Responses.of(service(authentication).arrive(id, body.actualKm()));
    }

    @Post("/{id}/reassign")
    @Secured(Permissions.TRIPS_ASSIGN)
    public HttpResponse<?> reassign(Authentication authentication, long id, @Body AssignWrite body) {
        return Responses.of(service(authentication).reassign(id, body.vehicleId(), body.operatorId()));
    }

    @Post("/{id}/cancel")
    @Secured(Permissions.TRIPS_WRITE)
    public HttpResponse<?> cancel(Authentication authentication, long id, @Body ReasonWrite body) {
        return Responses.of(service(authentication).cancel(id, body.reason()));
    }

    @Delete("/{id}")
    @Secured(Permissions.TRIPS_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private TripService service(Authentication authentication) {
        return new TripService(Callers.forAuthentication(authentication));
    }

    public record ArriveWrite(BigDecimal actualKm) {}

    public record AssignWrite(long vehicleId, long operatorId) {}

    public record ReasonWrite(String reason) {}
}
