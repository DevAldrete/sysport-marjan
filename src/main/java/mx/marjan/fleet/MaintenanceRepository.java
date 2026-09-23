package mx.marjan.fleet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.shared.Database;

public class MaintenanceRepository {

    private static final String BASE = """
            SELECT m.id, m.vehicle_id, m.maintenance_date, m.odometer_reading, m.maintenance_type,
                   m.work_performed, m.provider, m.cost, m.next_service_date, m.next_service_km,
                   CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label
            FROM maintenance m
            JOIN vehicles v ON v.id = m.vehicle_id
            """;

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
        return Database.queryList(BASE + " WHERE m.vehicle_id = ? ORDER BY m.maintenance_date DESC",
                this::map, vehicleId);
    }

    public long insert(java.sql.Connection connection, Maintenance maintenance) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "maintenance");
        Database.update(connection, """
                INSERT INTO maintenance
                  (id, vehicle_id, maintenance_date, odometer_reading, maintenance_type, work_performed,
                   provider, cost, next_service_date, next_service_km)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, maintenance.vehicleId(), maintenance.maintenanceDate(), maintenance.odometerReading(),
                maintenance.type().dbValue(), maintenance.workPerformed(), maintenance.provider(),
                maintenance.cost(), maintenance.nextServiceDate(), maintenance.nextServiceKm());
        return id;
    }

    public void delete(long id) {
        Database.update("DELETE FROM maintenance WHERE id = ?", id);
    }
}
