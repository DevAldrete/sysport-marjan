package mx.marjan.trips;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the trip stored procedures. */
public class TripRepository {

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
        String value = term == null || term.isBlank() ? null : term.trim();
        return Database.callList("{call sp_trips_search(?,?)}",
                this::map, value, status == null ? null : status.dbValue());
    }

    public Optional<Trip> findById(long id) {
        return Database.callOne("{call sp_trip_by_id(?)}", this::map, id);
    }

    public Optional<Trip> findByServiceRequest(long serviceRequestId) {
        return Database.callOne("{call sp_trip_by_request(?)}", this::map, serviceRequestId);
    }

    public Result<Long> assign(long requestId, long vehicleId, long operatorId, long userId) {
        Object[] out = Database.call("{call sp_assign_trip(?,?,?,?,?,?)}",
                new int[] { Types.VARCHAR, Types.BIGINT }, requestId, vehicleId, operatorId, userId);
        String problems = Database.asProblems(out[0]);
        return problems == null ? Result.ok(Database.asLong(out[1])) : Result.err(problems);
    }

    public Result<Void> reassign(long tripId, long vehicleId, long operatorId, long userId) {
        return Database.callVoid("{call sp_reassign_trip(?,?,?,?,?)}",
                tripId, vehicleId, operatorId, userId);
    }

    public Result<Void> depart(long tripId, long userId) {
        return Database.callVoid("{call sp_depart_trip(?,?,?)}", tripId, userId);
    }

    public Result<Void> arrive(long tripId, BigDecimal actualKm, long userId) {
        return Database.callVoid("{call sp_arrive_trip(?,?,?,?)}", tripId, actualKm, userId);
    }

    public Result<Void> cancel(long tripId, String reason, long userId) {
        return Database.callVoid("{call sp_cancel_trip(?,?,?,?)}", tripId, reason, userId);
    }

    public Result<Void> delete(long tripId, long userId) {
        return Database.callVoid("{call sp_trip_delete(?,?,?)}", tripId, userId);
    }

    /** Time-driven reconciliation: confirms dates and departs due trips. Returns rows changed. */
    public int sweepLifecycle(long userId) {
        Object[] out = Database.call("{call sp_sweep_lifecycle(?,?)}",
                new int[] { Types.INTEGER }, userId);
        return out[0] == null ? 0 : ((Number) out[0]).intValue();
    }
}
