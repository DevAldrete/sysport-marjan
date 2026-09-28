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
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.security.Permissions;
import mx.marjan.trips.Delivery;
import mx.marjan.trips.DeliveryService;
import mx.marjan.trips.DeliveryStatus;

/** Proof of delivery (FR-DEL-1). */
@Controller("/api/deliveries")
public class DeliveryController {

    @Get
    @Secured(Permissions.DELIVERIES_READ)
    public HttpResponse<?> byTrip(Authentication authentication, @QueryValue long tripId) {
        return service(authentication).findByTrip(tripId)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post
    @Secured(Permissions.DELIVERIES_WRITE)
    public HttpResponse<?> save(Authentication authentication, @Body Delivery delivery) {
        Delivery record = new Delivery(0, delivery.tripId(), null, delivery.actualDatetime(),
                delivery.receivedBy(), delivery.evidenceReference(),
                delivery.status() == null ? DeliveryStatus.PENDING_DOCUMENTS : delivery.status());
        return Responses.of(service(authentication).register(record));
    }

    @Delete("/{id}")
    @Secured(Permissions.DELIVERIES_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private DeliveryService service(Authentication authentication) {
        return new DeliveryService(Callers.forAuthentication(authentication));
    }
}
