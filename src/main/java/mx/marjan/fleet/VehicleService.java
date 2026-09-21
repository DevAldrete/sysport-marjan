package mx.marjan.fleet;

import java.math.BigDecimal;
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

    public List<Vehicle> listAssignable() {
        return vehicles.listAssignable();
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
        if (vehicle.loadCapacity() != null && vehicle.loadCapacity().signum() < 0) {
            problems.add("La capacidad de carga no puede ser negativa");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        if (vehicle.id() == 0) {
            long id = vehicles.insert(vehicle);
            return Result.ok(new Vehicle(id, vehicle.internalCode(), vehicle.plates(), vehicle.brand(),
                    vehicle.model(), vehicle.year(), vehicle.serialNumber(), vehicle.vehicleType(),
                    vehicle.loadCapacity(), vehicle.mileage(), vehicle.status()));
        }
        vehicles.update(vehicle);
        return Result.ok(vehicle);
    }

    public Result<Void> setStatus(long id, VehicleStatus status) {
        if (!Session.has(Permissions.FLEET_WRITE)) {
            return Result.err("No tiene permiso para modificar unidades");
        }
        vehicles.updateStatus(id, status);
        return Result.ok(null);
    }

    public void updateMileageIfHigher(long id, BigDecimal reading) {
        vehicles.updateMileageIfHigher(id, reading);
    }
}
