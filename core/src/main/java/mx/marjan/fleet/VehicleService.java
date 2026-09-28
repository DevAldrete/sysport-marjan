package mx.marjan.fleet;

import java.util.List;
import java.util.Optional;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.shared.Result;

public class VehicleService {

    private final VehicleRepository vehicles = new VehicleRepository();
    private final Caller caller;

    public VehicleService(Caller caller) {
        this.caller = caller;
    }

    public List<Vehicle> search(String term) {
        return vehicles.search(term);
    }

    public Optional<Vehicle> find(long id) {
        return vehicles.findById(id);
    }

    public Result<Vehicle> save(Vehicle vehicle) {
        if (!caller.has(Permissions.FLEET_WRITE)) {
            return Result.err("No tiene permiso para modificar unidades");
        }
        Result<Long> saved = vehicles.save(vehicle);
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return Result.ok(new Vehicle(saved.value(), vehicle.internalCode(), vehicle.plates(),
                vehicle.brand(), vehicle.model(), vehicle.year(), vehicle.serialNumber(),
                vehicle.vehicleType(), vehicle.loadCapacity(), vehicle.mileage(), vehicle.status()));
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.FLEET_WRITE)) {
            return Result.err("No tiene permiso para eliminar unidades");
        }
        return vehicles.delete(id);
    }

    public Result<Void> setStatus(long id, VehicleStatus status) {
        if (!caller.has(Permissions.FLEET_WRITE)) {
            return Result.err("No tiene permiso para modificar unidades");
        }
        return vehicles.setStatus(id, status);
    }
}
