package mx.marjan.trips;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the incident stored procedures. */
public class IncidentRepository {

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
        return Database.callList("{call sp_incidents_by_trip(?)}", this::map, tripId);
    }

    public Result<Long> save(Incident incident, long userId) {
        return Database.callForId("{call sp_incident_save(?,?,?,?,?,?,?,?,?,?)}",
                incident.tripId(), incident.incidentDate(), incident.incidentTime(),
                incident.location(), incident.type().dbValue(), incident.description(),
                incident.actionsTaken(), userId);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_incident_delete(?)}", id);
    }
}
