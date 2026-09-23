package mx.marjan.fleet;

import java.util.List;
import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

public class FuelService {

    private final FuelLoadRepository loads = new FuelLoadRepository();
    private final VehicleRepository vehicles = new VehicleRepository();

    public List<FuelLoad> listByVehicle(long vehicleId) {
        return loads.listByVehicle(vehicleId);
    }

    public List<FuelLoad> listByTrip(long tripId) {
        return loads.listByTrip(tripId);
    }

    public List<FuelLoad> listAll() {
        return loads.listAll();
    }

    /** BR-18: consistency checks, vehicle/trip match, and mileage update in one transaction. */
    public Result<Void> register(FuelLoad load) {
        if (!Session.has(Permissions.FUEL_WRITE)) {
            return Result.err("No tiene permiso para registrar cargas de combustible");
        }
        if (load.vehicleId() == 0) {
            return Result.err("Debe seleccionar una unidad");
        }
        if (load.loadDate() == null) {
            return Result.err("La fecha de carga es obligatoria");
        }
        Optional<Vehicle> vehicle = vehicles.findById(load.vehicleId());
        if (vehicle.isEmpty()) {
            return Result.err("La unidad seleccionada no existe");
        }
        Result<Void> validated = FuelRules.validate(load, vehicle.get().mileage());
        if (validated.isErr()) {
            return validated;
        }
        return Database.inTransaction(connection -> {
            if (load.tripId() != null) {
                java.util.OptionalLong tripVehicle = loads.vehicleIdForTrip(load.tripId());
                if (tripVehicle.isPresent() && tripVehicle.getAsLong() != load.vehicleId()) {
                    return Result.<Void>err("La unidad de la carga no coincide con la unidad del viaje");
                }
            }
            loads.insert(connection, load);
            vehicles.updateMileageIfHigher(connection, load.vehicleId(), load.odometerReading());
            return Result.<Void>ok(null);
        });
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.FUEL_WRITE)) {
            return Result.err("No tiene permiso para eliminar cargas de combustible");
        }
        loads.delete(id);
        return Result.ok(null);
    }

}
