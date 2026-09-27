package mx.marjan.fleet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the vehicle stored procedures. */
public class VehicleRepository {

    private Vehicle map(ResultSet rs) throws SQLException {
        return new Vehicle(
                rs.getLong("id"),
                rs.getString("internal_code"),
                rs.getString("plates"),
                rs.getString("brand"),
                rs.getString("model"),
                (Integer) rs.getObject("year"),
                rs.getString("serial_number"),
                rs.getString("vehicle_type"),
                rs.getBigDecimal("load_capacity"),
                rs.getBigDecimal("mileage"),
                VehicleStatus.fromDb(rs.getString("status")));
    }

    public List<Vehicle> search(String term) {
        String value = term == null || term.isBlank() ? null : term.trim();
        return Database.callList("{call sp_vehicles_search(?)}", this::map, value);
    }

    public Optional<Vehicle> findById(long id) {
        return Database.callOne("{call sp_vehicle_by_id(?)}", this::map, id);
    }

    public List<Vehicle> listEligible(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return Database.callList("{call sp_eligible_vehicles_full(?,?)}", this::map, start, end);
    }

    public Result<Long> save(Vehicle vehicle) {
        return Database.callForId("{call sp_vehicle_save(?,?,?,?,?,?,?,?,?,?,?,?,?)}",
                vehicle.id(), vehicle.internalCode(), vehicle.plates(), vehicle.brand(),
                vehicle.model(), vehicle.year(), vehicle.serialNumber(), vehicle.vehicleType(),
                vehicle.loadCapacity(), vehicle.mileage(), vehicle.status().dbValue());
    }

    public Result<Void> setStatus(long id, VehicleStatus status) {
        Database.callNoOut("{call sp_vehicle_set_status(?,?)}", id, status.dbValue());
        return Result.ok(null);
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_vehicle_delete(?,?)}", id);
    }

    /** BR-21: mileage never decreases. */
    public void raiseMileage(long id, java.math.BigDecimal reading) {
        Database.callNoOut("{call sp_vehicle_raise_mileage(?,?)}", id, reading);
    }
}
