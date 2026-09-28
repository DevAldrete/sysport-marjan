package mx.marjan.fleet;

import java.util.List;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.shared.Result;

public class MaintenanceService {

    private final MaintenanceRepository maintenance = new MaintenanceRepository();
    private final Caller caller;

    public MaintenanceService(Caller caller) {
        this.caller = caller;
    }

    public List<Maintenance> listByVehicle(long vehicleId) {
        return maintenance.listByVehicle(vehicleId);
    }

    /** BR-21: the database validates and advances the vehicle mileage when appropriate. */
    public Result<Void> register(Maintenance record) {
        if (!caller.has(Permissions.FLEET_MAINTENANCE)) {
            return Result.err("No tiene permiso para registrar mantenimiento");
        }
        Result<Long> saved = maintenance.save(record, caller.userId());
        return saved.isOk() ? Result.ok(null) : Result.err(saved.problems());
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.FLEET_MAINTENANCE)) {
            return Result.err("No tiene permiso para eliminar mantenimiento");
        }
        maintenance.delete(id);
        return Result.ok(null);
    }
}
