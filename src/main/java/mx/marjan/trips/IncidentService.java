package mx.marjan.trips;

import java.util.List;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class IncidentService {

    private final IncidentRepository incidents = new IncidentRepository();

    public List<Incident> listByTrip(long tripId) {
        return incidents.listByTrip(tripId);
    }

    public Result<Void> register(Incident incident) {
        if (!Session.has(Permissions.INCIDENTS_WRITE)) {
            return Result.err("No tiene permiso para registrar incidencias");
        }
        Result<Long> saved = incidents.save(incident, Session.userId());
        return saved.isOk() ? Result.ok(null) : Result.err(saved.problems());
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.INCIDENTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar incidencias");
        }
        incidents.delete(id);
        return Result.ok(null);
    }
}
