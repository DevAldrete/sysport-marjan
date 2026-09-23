package mx.marjan.fleet;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

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
        if (term == null || term.isBlank()) {
            return Database.queryList("SELECT * FROM vehicles ORDER BY internal_code", this::map);
        }
        String like = "%" + term.trim() + "%";
        return Database.queryList("""
                SELECT * FROM vehicles
                WHERE internal_code LIKE ? OR plates LIKE ? OR brand LIKE ? OR model LIKE ?
                ORDER BY internal_code
                """, this::map, like, like, like, like);
    }

    public Optional<Vehicle> findById(long id) {
        return Database.queryOne("SELECT * FROM vehicles WHERE id = ?", this::map, id);
    }

    public Optional<Vehicle> findById(java.sql.Connection connection, long id) throws SQLException {
        return Database.queryOne(connection, "SELECT * FROM vehicles WHERE id = ?", this::map, id);
    }

    /** FR-TRP-1: assignable vehicles with no overlapping active trip in the window. */
    public List<Vehicle> listEligible(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return Database.queryList("""
                SELECT * FROM vehicles v
                WHERE v.status = 'available'
                  AND NOT EXISTS (
                    SELECT 1 FROM trips t
                    WHERE t.vehicle_id = v.id
                      AND t.status IN ('scheduled', 'in_transit')
                      AND t.planned_start < ? AND t.planned_end > ?)
                ORDER BY v.internal_code
                """, this::map, end, start);
    }

    public long insert(java.sql.Connection connection, Vehicle vehicle) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "vehicles");
        Database.update(connection, """
                INSERT INTO vehicles
                  (id, internal_code, plates, brand, model, year, serial_number, vehicle_type,
                   load_capacity, mileage, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, vehicle.internalCode(), vehicle.plates(), vehicle.brand(), vehicle.model(),
                vehicle.year(), vehicle.serialNumber(), vehicle.vehicleType(),
                vehicle.loadCapacity(), vehicle.mileage(), vehicle.status().dbValue());
        return id;
    }

    public void update(Vehicle vehicle) {
        Database.update("""
                UPDATE vehicles SET
                  internal_code = ?, plates = ?, brand = ?, model = ?, year = ?, serial_number = ?,
                  vehicle_type = ?, load_capacity = ?, mileage = ?, status = ?
                WHERE id = ?
                """,
                vehicle.internalCode(), vehicle.plates(), vehicle.brand(), vehicle.model(),
                vehicle.year(), vehicle.serialNumber(), vehicle.vehicleType(),
                vehicle.loadCapacity(), vehicle.mileage(), vehicle.status().dbValue(), vehicle.id());
    }

    public void updateStatus(long id, VehicleStatus status) {
        Database.update("UPDATE vehicles SET status = ? WHERE id = ?", status.dbValue(), id);
    }

    public void delete(long id) {
        Database.update("DELETE FROM vehicles WHERE id = ?", id);
    }

    public void updateStatus(java.sql.Connection connection, long id, VehicleStatus status) throws SQLException {
        Database.update(connection, "UPDATE vehicles SET status = ? WHERE id = ?", status.dbValue(), id);
    }

    /** BR-21: mileage never decreases. */
    public void updateMileageIfHigher(java.sql.Connection connection, long id, BigDecimal reading)
            throws SQLException {
        if (reading == null) {
            return;
        }
        Database.update(connection, "UPDATE vehicles SET mileage = ? WHERE id = ? AND mileage < ?",
                reading, id, reading);
    }
}
