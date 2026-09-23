package mx.marjan.trips;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import mx.marjan.shared.Database;

public class IncidentRepository {

    private static final String BASE = """
            SELECT i.id, i.trip_id, sr.folio, i.incident_date, i.incident_time,
                   i.location, i.incident_type, i.description, i.actions_taken
            FROM incidents i
            JOIN trips t ON t.id = i.trip_id
            JOIN service_requests sr ON sr.id = t.service_request_id
            """;

    private Incident map(ResultSet rs) throws SQLException {
        return new Incident(
                rs.getLong("id"),
                rs.getLong("trip_id"),
                rs.getString("folio"),
                rs.getObject("incident_date", LocalDate.class),
                rs.getObject("incident_time", LocalTime.class),
                rs.getString("location"),
                IncidentType.fromDb(rs.getString("incident_type")),
                rs.getString("description"),
                rs.getString("actions_taken"));
    }

    public List<Incident> listByTrip(long tripId) {
        return Database.queryList(BASE + " WHERE i.trip_id = ? ORDER BY i.incident_date, i.incident_time",
                this::map, tripId);
    }

    public long insert(java.sql.Connection connection, Incident incident) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "incidents");
        Database.update(connection, """
                INSERT INTO incidents
                  (id, trip_id, incident_date, incident_time, location, incident_type,
                   description, actions_taken, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, incident.tripId(), incident.incidentDate(), incident.incidentTime(),
                incident.location(), incident.type().dbValue(), incident.description(),
                incident.actionsTaken(), mx.marjan.security.Session.userId());
        return id;
    }

    public void delete(long id) {
        Database.update("DELETE FROM incidents WHERE id = ?", id);
    }
}
