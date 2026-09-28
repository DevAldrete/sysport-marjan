package mx.marjan.trips;

import java.util.List;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.security.SessionCaller;
import mx.marjan.shared.Result;

public class IncidentService {

    private final IncidentRepository incidents = new IncidentRepository();
    private final Caller caller;

    public IncidentService() {
        this(SessionCaller.INSTANCE);
    }

    public IncidentService(Caller caller) {
        this.caller = caller;
    }

    public List<Incident> listByTrip(long tripId) {
        return incidents.listByTrip(tripId);
    }

    public Result<Void> register(Incident incident) {
        if (!caller.has(Permissions.INCIDENTS_WRITE)) {
            return Result.err("No tiene permiso para registrar incidencias");
        }
        Result<Long> saved = incidents.save(incident, caller.userId());
        return saved.isOk() ? Result.ok(null) : Result.err(saved.problems());
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.INCIDENTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar incidencias");
        }
        incidents.delete(id);
        return Result.ok(null);
    }
}
