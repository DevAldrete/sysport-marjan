package mx.marjan.trips;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

public class TripRepository {

    private static final String BASE = """
            SELECT t.id, t.service_request_id, sr.folio, c.name AS client_name,
                   CONCAT(r.origin, ' -> ', r.destination) AS route_label,
                   t.vehicle_id, CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label,
                   t.employee_id, e.name AS employee_name,
                   t.estimated_km, t.actual_km, t.planned_start, t.planned_end,
                   t.departure_datetime, t.arrival_datetime, t.status
            FROM trips t
            JOIN service_requests sr ON sr.id = t.service_request_id
            JOIN clients c ON c.id = sr.client_id
            JOIN routes r ON r.id = sr.route_id
            JOIN vehicles v ON v.id = t.vehicle_id
            JOIN employees e ON e.id = t.employee_id
            """;

    private Trip map(ResultSet rs) throws SQLException {
        return new Trip(
                rs.getLong("id"),
                rs.getLong("service_request_id"),
                rs.getString("folio"),
                rs.getString("client_name"),
                rs.getString("route_label"),
                rs.getLong("vehicle_id"),
                rs.getString("vehicle_label"),
                rs.getLong("employee_id"),
                rs.getString("employee_name"),
                rs.getBigDecimal("estimated_km"),
                rs.getBigDecimal("actual_km"),
                rs.getObject("planned_start", LocalDateTime.class),
                rs.getObject("planned_end", LocalDateTime.class),
                rs.getObject("departure_datetime", LocalDateTime.class),
                rs.getObject("arrival_datetime", LocalDateTime.class),
                TripStatus.fromDb(rs.getString("status")));
    }

    public List<Trip> search(String term, TripStatus status) {
        StringBuilder sql = new StringBuilder(BASE).append(" WHERE 1 = 1");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (term != null && !term.isBlank()) {
            sql.append(" AND (sr.folio LIKE ? OR c.name LIKE ? OR v.internal_code LIKE ? OR e.name LIKE ?)");
            String like = "%" + term.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null) {
            sql.append(" AND t.status = ?");
            params.add(status.dbValue());
        }
        sql.append(" ORDER BY t.planned_start DESC");
        return Database.queryList(sql.toString(), this::map, params.toArray());
    }

    public Optional<Trip> findById(long id) {
        return Database.queryOne(BASE + " WHERE t.id = ?", this::map, id);
    }

    public Optional<Trip> findById(Connection connection, long id) throws SQLException {
        return Database.queryOne(connection, BASE + " WHERE t.id = ?", this::map, id);
    }

    public Optional<Trip> findByServiceRequest(long serviceRequestId) {
        return Database.queryOne(BASE + " WHERE t.service_request_id = ?", this::map, serviceRequestId);
    }

    public Optional<Trip> findByServiceRequest(Connection connection, long serviceRequestId) throws SQLException {
        return Database.queryOne(connection, BASE + " WHERE t.service_request_id = ?",
                this::map, serviceRequestId);
    }

    /** BR-05: active trips of a vehicle that overlap [start, end), excluding one trip id. */
    public List<Trip> overlappingForVehicle(Connection connection, long vehicleId,
            LocalDateTime start, LocalDateTime end, long excludeTripId) throws SQLException {
        return Database.queryList(connection, BASE + """
                WHERE t.vehicle_id = ?
                  AND t.id <> ?
                  AND t.status IN ('scheduled', 'in_transit')
                  AND t.planned_start < ? AND t.planned_end > ?
                """, this::map, vehicleId, excludeTripId, end, start);
    }

    /** BR-06: active trips of an operator that overlap [start, end), excluding one trip id. */
    public List<Trip> overlappingForEmployee(Connection connection, long employeeId,
            LocalDateTime start, LocalDateTime end, long excludeTripId) throws SQLException {
        return Database.queryList(connection, BASE + """
                WHERE t.employee_id = ?
                  AND t.id <> ?
                  AND t.status IN ('scheduled', 'in_transit')
                  AND t.planned_start < ? AND t.planned_end > ?
                """, this::map, employeeId, excludeTripId, end, start);
    }

    public void lockVehicle(Connection connection, long vehicleId) throws SQLException {
        Database.queryOne(connection, "SELECT id FROM vehicles WHERE id = ? FOR UPDATE",
                rs -> rs.getLong(1), vehicleId);
    }

    public void lockEmployee(Connection connection, long employeeId) throws SQLException {
        Database.queryOne(connection, "SELECT id FROM employees WHERE id = ? FOR UPDATE",
                rs -> rs.getLong(1), employeeId);
    }

    public long insert(Connection connection, AssignmentPlan plan, BigDecimal estimatedKm, long userId)
            throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "trips");
        Database.update(connection, """
                INSERT INTO trips
                  (id, service_request_id, vehicle_id, employee_id, estimated_km,
                   planned_start, planned_end, status, created_by, updated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'scheduled', ?, ?)
                """,
                id, plan.requestId(), plan.vehicleId(), plan.employeeId(), estimatedKm,
                plan.plannedStart(), plan.plannedEnd(), userId, userId);
        return id;
    }

    public void delete(long id) {
        Database.update("DELETE FROM trips WHERE id = ?", id);
    }

    public void depart(Connection connection, long id, LocalDateTime departure, long userId) throws SQLException {
        Database.update(connection, """
                UPDATE trips SET departure_datetime = ?, status = 'in_transit', updated_by = ?
                WHERE id = ?
                """, departure, userId, id);
    }

    public void arrive(Connection connection, long id, LocalDateTime arrival, BigDecimal actualKm, long userId)
            throws SQLException {
        Database.update(connection, """
                UPDATE trips SET arrival_datetime = ?, actual_km = ?, status = 'completed', updated_by = ?
                WHERE id = ?
                """, arrival, actualKm, userId, id);
    }

    public void reassign(Connection connection, long id, long vehicleId, long employeeId, long userId)
            throws SQLException {
        Database.update(connection, """
                UPDATE trips SET vehicle_id = ?, employee_id = ?, updated_by = ?
                WHERE id = ?
                """, vehicleId, employeeId, userId, id);
    }

    public void updateStatus(Connection connection, long id, TripStatus status, long userId) throws SQLException {
        Database.update(connection, "UPDATE trips SET status = ?, updated_by = ? WHERE id = ?",
                status.dbValue(), userId, id);
    }
}
