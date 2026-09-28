package mx.marjan.fleet;

import java.util.List;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.shared.Result;

public class FuelService {

    private final FuelLoadRepository loads = new FuelLoadRepository();
    private final Caller caller;

    public FuelService(Caller caller) {
        this.caller = caller;
    }

    public List<FuelLoad> listByVehicle(long vehicleId) {
        return loads.listByVehicle(vehicleId);
    }

    public List<FuelLoad> listByTrip(long tripId) {
        return loads.listByTrip(tripId);
    }

    public List<FuelLoad> listAll() {
        return loads.listAll();
    }

    /** BR-18: consistency checks, vehicle/trip match and mileage update run in the database. */
    public Result<Void> register(FuelLoad load) {
        if (!caller.has(Permissions.FUEL_WRITE)) {
            return Result.err("No tiene permiso para registrar cargas de combustible");
        }
        Result<Long> saved = loads.save(load, caller.userId());
        return saved.isOk() ? Result.ok(null) : Result.err(saved.problems());
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.FUEL_WRITE)) {
            return Result.err("No tiene permiso para eliminar cargas de combustible");
        }
        loads.delete(id);
        return Result.ok(null);
    }
}
