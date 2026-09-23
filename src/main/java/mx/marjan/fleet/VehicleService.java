package mx.marjan.fleet;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class VehicleService {

    private final VehicleRepository vehicles = new VehicleRepository();

    public List<Vehicle> search(String term) {
        return vehicles.search(term);
    }

    public Optional<Vehicle> find(long id) {
        return vehicles.findById(id);
    }

    public Result<Vehicle> save(Vehicle vehicle) {
        if (!Session.has(Permissions.FLEET_WRITE)) {
            return Result.err("No tiene permiso para modificar unidades");
        }
        List<String> problems = new ArrayList<>();
        if (vehicle.internalCode() == null || vehicle.internalCode().isBlank()) {
            problems.add("El numero economico es obligatorio");
        }
        if (vehicle.plates() == null || vehicle.plates().isBlank()) {
            problems.add("Las placas son obligatorias");
        }
        if (!mx.marjan.shared.Validators.isMeasure(vehicle.loadCapacity())) {
            problems.add("La capacidad de carga es invalida o excede el maximo permitido");
        }
        if (!mx.marjan.shared.Validators.isMeasure(vehicle.mileage())) {
            problems.add("El kilometraje es invalido o excede el maximo permitido");
        }
        if (!mx.marjan.shared.Validators.isValidYear(vehicle.year())) {
            problems.add("El anio debe estar entre " + mx.marjan.shared.Validators.MIN_VEHICLE_YEAR
                    + " y " + (java.time.LocalDate.now().getYear() + 1));
        }
        if (!mx.marjan.shared.Validators.isValidPlates(vehicle.plates())) {
            problems.add("Las placas no tienen un formato valido");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        if (vehicle.id() == 0) {
            Long id = mx.marjan.shared.Database.inTransaction(connection -> vehicles.insert(connection, vehicle));
            return Result.ok(new Vehicle(id, vehicle.internalCode(), vehicle.plates(), vehicle.brand(),
                    vehicle.model(), vehicle.year(), vehicle.serialNumber(), vehicle.vehicleType(),
                    vehicle.loadCapacity(), vehicle.mileage(), vehicle.status()));
        }
        vehicles.update(vehicle);
        return Result.ok(vehicle);
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.FLEET_WRITE)) {
            return Result.err("No tiene permiso para eliminar unidades");
        }
        vehicles.delete(id);
        return Result.ok(null);
    }

    public Result<Void> setStatus(long id, VehicleStatus status) {
        if (!Session.has(Permissions.FLEET_WRITE)) {
            return Result.err("No tiene permiso para modificar unidades");
        }
        vehicles.updateStatus(id, status);
        return Result.ok(null);
    }
}
