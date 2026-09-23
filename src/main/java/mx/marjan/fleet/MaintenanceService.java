package mx.marjan.fleet;

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
        } else if (!mx.marjan.shared.Validators.isValidDate(record.maintenanceDate())) {
            problems.add("La fecha de mantenimiento no es valida");
        }
        if (!mx.marjan.shared.Validators.isMoney(record.cost())) {
            problems.add("El costo es invalido o excede el maximo permitido");
        }
        if (!mx.marjan.shared.Validators.isMeasure(record.odometerReading())) {
            problems.add("El odometro es invalido o excede el maximo permitido");
        }
        if (!mx.marjan.shared.Validators.isMeasure(record.nextServiceKm())) {
            problems.add("El proximo kilometraje es invalido o excede el maximo permitido");
        }
        if (record.nextServiceDate() != null && record.maintenanceDate() != null
                && record.nextServiceDate().isBefore(record.maintenanceDate())) {
            problems.add("La proxima fecha de servicio no puede ser anterior a la del mantenimiento");
        }
        if (record.nextServiceKm() != null && record.odometerReading() != null
                && record.nextServiceKm().compareTo(record.odometerReading()) < 0) {
            problems.add("El proximo kilometraje no puede ser menor al odometro actual");
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

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.FLEET_MAINTENANCE)) {
            return Result.err("No tiene permiso para eliminar mantenimiento");
        }
        maintenance.delete(id);
        return Result.ok(null);
    }
}
