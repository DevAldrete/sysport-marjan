package mx.marjan.fleet;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

public class MaintenanceService {

    private final MaintenanceRepository maintenance = new MaintenanceRepository();
    private final VehicleRepository vehicles = new VehicleRepository();

    public List<Maintenance> listByVehicle(long vehicleId) {
        return maintenance.listByVehicle(vehicleId);
    }

    public List<Maintenance> listAll() {
        return maintenance.listAll();
    }

    public List<Maintenance> listDue(LocalDate today) {
        return maintenance.listDue(today);
    }

    /** BR-21: registering maintenance advances the vehicle mileage when its odometer is higher. */
    public Result<Void> register(Maintenance record) {
        if (!Session.has(Permissions.FLEET_MAINTENANCE)) {
            return Result.err("No tiene permiso para registrar mantenimiento");
        }
        List<String> problems = new ArrayList<>();
        if (record.vehicleId() == 0) {
            problems.add("Debe seleccionar una unidad");
        }
        if (record.maintenanceDate() == null) {
            problems.add("La fecha de mantenimiento es obligatoria");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        Database.inTransaction(connection -> {
            maintenance.insert(connection, record);
            vehicles.updateMileageIfHigher(connection, record.vehicleId(), record.odometerReading());
            return null;
        });
        return Result.ok(null);
    }
}
