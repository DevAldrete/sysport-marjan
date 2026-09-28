package mx.marjan.trips;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the delivery stored procedures. */
public class DeliveryRepository {

    private Delivery map(ResultSet rs) throws SQLException {
        return new Delivery(
                rs.getLong("id"),
                rs.getLong("trip_id"),
                rs.getString("folio"),
                rs.getObject("actual_datetime", LocalDateTime.class),
                rs.getString("received_by"),
                rs.getString("evidence_reference"),
                DeliveryStatus.fromDb(rs.getString("status")));
    }

    public Optional<Delivery> findByTrip(long tripId) {
        return Database.callOne("{call sp_delivery_by_trip(?)}", this::map, tripId);
    }

    public Result<Long> save(Delivery delivery, long userId) {
        return Database.callForId("{call sp_delivery_save(?,?,?,?,?,?,?,?)}",
                delivery.tripId(), delivery.actualDatetime(), delivery.receivedBy(),
                delivery.evidenceReference(), delivery.status().dbValue(), userId);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_delivery_delete(?)}", id);
    }
}
