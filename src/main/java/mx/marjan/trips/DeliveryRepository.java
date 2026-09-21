package mx.marjan.trips;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

public class DeliveryRepository {

    private static final String BASE = """
            SELECT d.id, d.trip_id, sr.folio, d.actual_datetime, d.received_by,
                   d.evidence_reference, d.status
            FROM deliveries d
            JOIN trips t ON t.id = d.trip_id
            JOIN service_requests sr ON sr.id = t.service_request_id
            """;

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
        return Database.queryOne(BASE + " WHERE d.trip_id = ?", this::map, tripId);
    }

    public Optional<Delivery> findByTrip(java.sql.Connection connection, long tripId) throws SQLException {
        return Database.queryOne(connection, BASE + " WHERE d.trip_id = ?", this::map, tripId);
    }

    public List<Delivery> listAll() {
        return Database.queryList(BASE + " ORDER BY d.actual_datetime DESC", this::map);
    }

    public void save(java.sql.Connection connection, Delivery delivery, long userId) throws SQLException {
        Optional<Delivery> existing = findByTrip(connection, delivery.tripId());
        if (existing.isEmpty()) {
            Database.insertReturningId(connection, """
                    INSERT INTO deliveries
                      (trip_id, actual_datetime, received_by, evidence_reference, status, created_by)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    delivery.tripId(), delivery.actualDatetime(), delivery.receivedBy(),
                    delivery.evidenceReference(), delivery.status().dbValue(), userId);
        } else {
            Database.update(connection, """
                    UPDATE deliveries SET actual_datetime = ?, received_by = ?, evidence_reference = ?, status = ?
                    WHERE trip_id = ?
                    """,
                    delivery.actualDatetime(), delivery.receivedBy(), delivery.evidenceReference(),
                    delivery.status().dbValue(), delivery.tripId());
        }
    }
}
