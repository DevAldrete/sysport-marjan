package mx.marjan.fleet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the maintenance stored procedures. */
public class MaintenanceRepository {

    private Maintenance map(ResultSet rs) throws SQLException {
        return new Maintenance(
                rs.getLong("id"),
                rs.getLong("vehicle_id"),
                rs.getString("vehicle_label"),
                rs.getObject("maintenance_date", LocalDate.class),
                rs.getBigDecimal("odometer_reading"),
                MaintenanceType.fromDb(rs.getString("maintenance_type")),
                rs.getString("work_performed"),
                rs.getString("provider"),
                rs.getBigDecimal("cost"),
                rs.getObject("next_service_date", LocalDate.class),
                rs.getBigDecimal("next_service_km"));
    }

    public List<Maintenance> listByVehicle(long vehicleId) {
        return Database.callList("{call sp_maintenance_by_vehicle(?)}", this::map, vehicleId);
    }

    public Result<Long> save(Maintenance record, long userId) {
        return Database.callForId("{call sp_maintenance_save(?,?,?,?,?,?,?,?,?,?,?,?)}",
                record.vehicleId(), record.maintenanceDate(), record.odometerReading(),
                record.type().dbValue(), record.workPerformed(), record.provider(), record.cost(),
                record.nextServiceDate(), record.nextServiceKm(), userId);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_maintenance_delete(?)}", id);
    }
}
