package mx.marjan.api.fleet;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.fleet.FuelLoad;
import mx.marjan.fleet.FuelService;
import mx.marjan.security.Permissions;

/** Fuel loads (FR-FUEL-1). */
@Controller("/api/fuel")
public class FuelController {

    @Get
    @Secured(Permissions.FUEL_READ)
    public List<FuelLoad> list(Authentication authentication) {
        return service(authentication).listAll();
    }

    @Get("/vehicle/{vehicleId}")
    @Secured(Permissions.FUEL_READ)
    public List<FuelLoad> byVehicle(Authentication authentication, long vehicleId) {
        return service(authentication).listByVehicle(vehicleId);
    }

    @Get("/trip/{tripId}")
    @Secured(Permissions.FUEL_READ)
    public List<FuelLoad> byTrip(Authentication authentication, long tripId) {
        return service(authentication).listByTrip(tripId);
    }

    @Post
    @Secured(Permissions.FUEL_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body FuelLoad load) {
        return Responses.of(service(authentication).register(load));
    }

    @Delete("/{id}")
    @Secured(Permissions.FUEL_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private FuelService service(Authentication authentication) {
        return new FuelService(Callers.forAuthentication(authentication));
    }
}
