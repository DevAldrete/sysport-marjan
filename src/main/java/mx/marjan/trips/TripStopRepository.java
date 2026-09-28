package mx.marjan.trips;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the trip-stop arrival procedures (BR-25). */
public class TripStopRepository {

    private TripStop map(ResultSet rs) throws SQLException {
        return new TripStop(
                rs.getLong("route_stop_id"),
                rs.getInt("sequence_no"),
                rs.getString("location"),
                rs.getObject("arrival_id", Long.class),
                rs.getObject("arrived_at", LocalDateTime.class),
                rs.getString("notes"));
    }

    public List<TripStop> listByTrip(long tripId) {
        return Database.callList("{call sp_trip_stops(?)}", this::map, tripId);
    }

    public Result<Void> saveArrival(long tripId, long routeStopId, LocalDateTime arrivedAt,
            String notes, long userId) {
        return Database.callVoid("{call sp_trip_stop_arrival_save(?,?,?,?,?,?)}",
                tripId, routeStopId, arrivedAt, notes, userId);
    }

    public void deleteArrival(long arrivalId) {
        Database.callNoOut("{call sp_trip_stop_arrival_delete(?)}", arrivalId);
    }
}
