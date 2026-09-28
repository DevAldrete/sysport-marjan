package mx.marjan.api.fleet;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.math.BigDecimal;
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.fleet.Maintenance;
import mx.marjan.fleet.MaintenanceService;
import mx.marjan.fleet.MaintenanceType;
import mx.marjan.fleet.Vehicle;
import mx.marjan.fleet.VehicleService;
import mx.marjan.fleet.VehicleStatus;
import mx.marjan.security.Permissions;

/** Vehicles, their maintenance history and status (FR-VEH-1/2, FR-MNT-1/2). */
@Controller("/api/vehicles")
public class VehicleController {

    @Get
    @Secured(Permissions.FLEET_READ)
    public List<Vehicle> search(Authentication authentication, @Nullable @QueryValue String term) {
        return new VehicleService(Callers.forAuthentication(authentication)).search(term);
    }

    @Get("/{id}")
    @Secured(Permissions.FLEET_READ)
    public HttpResponse<?> find(Authentication authentication, long id) {
        return new VehicleService(Callers.forAuthentication(authentication)).find(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post
    @Secured(Permissions.FLEET_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body Vehicle vehicle) {
        return Responses.of(new VehicleService(Callers.forAuthentication(authentication))
                .save(normalize(vehicle, 0)));
    }

    @Put("/{id}")
    @Secured(Permissions.FLEET_WRITE)
    public HttpResponse<?> update(Authentication authentication, long id, @Body Vehicle vehicle) {
        return Responses.of(new VehicleService(Callers.forAuthentication(authentication))
                .save(normalize(vehicle, id)));
    }

    @Post("/{id}/status")
    @Secured(Permissions.FLEET_WRITE)
    public HttpResponse<?> changeStatus(Authentication authentication, long id,
            @Body StatusUpdate body) {
        return Responses.of(new VehicleService(Callers.forAuthentication(authentication))
                .setStatus(id, body.status() == null ? VehicleStatus.AVAILABLE : body.status()));
    }

    @Delete("/{id}")
    @Secured(Permissions.FLEET_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(new VehicleService(Callers.forAuthentication(authentication)).delete(id));
    }

    @Get("/{id}/maintenance")
    @Secured(Permissions.FLEET_READ)
    public List<Maintenance> maintenance(Authentication authentication, long id) {
        return new MaintenanceService(Callers.forAuthentication(authentication)).listByVehicle(id);
    }

    @Post("/{id}/maintenance")
    @Secured(Permissions.FLEET_MAINTENANCE)
    public HttpResponse<?> addMaintenance(Authentication authentication, long id,
            @Body Maintenance body) {
        Maintenance record = new Maintenance(0, id, null, body.maintenanceDate(),
                body.odometerReading(),
                body.type() == null ? MaintenanceType.PREVENTIVE : body.type(),
                body.workPerformed(), body.provider(), body.cost(), body.nextServiceDate(),
                body.nextServiceKm());
        return Responses.of(
                new MaintenanceService(Callers.forAuthentication(authentication)).register(record));
    }

    @Delete("/maintenance/{maintenanceId}")
    @Secured(Permissions.FLEET_MAINTENANCE)
    public HttpResponse<?> deleteMaintenance(Authentication authentication, long maintenanceId) {
        return Responses.of(
                new MaintenanceService(Callers.forAuthentication(authentication)).delete(maintenanceId));
    }

    private Vehicle normalize(Vehicle vehicle, long id) {
        return new Vehicle(id, vehicle.internalCode(), vehicle.plates(), vehicle.brand(),
                vehicle.model(), vehicle.year(), vehicle.serialNumber(), vehicle.vehicleType(),
                vehicle.loadCapacity() == null ? BigDecimal.ZERO : vehicle.loadCapacity(),
                vehicle.mileage() == null ? BigDecimal.ZERO : vehicle.mileage(),
                vehicle.status() == null ? VehicleStatus.AVAILABLE : vehicle.status());
    }

    public record StatusUpdate(VehicleStatus status) {}
}
