package mx.marjan.trips;

import java.util.ArrayList;
import java.util.List;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class IncidentService {

    private final IncidentRepository incidents = new IncidentRepository();

    public List<Incident> listByTrip(long tripId) {
        return incidents.listByTrip(tripId);
    }

    public List<Incident> listAll() {
        return incidents.listAll();
    }

    public Result<Void> register(Incident incident) {
        if (!Session.has(Permissions.INCIDENTS_WRITE)) {
            return Result.err("No tiene permiso para registrar incidencias");
        }
        List<String> problems = new ArrayList<>();
        if (incident.tripId() == 0) {
            problems.add("Debe seleccionar un viaje");
        }
        if (incident.incidentDate() == null) {
            problems.add("La fecha de la incidencia es obligatoria");
        }
        if (incident.description() == null || incident.description().isBlank()) {
            problems.add("La descripcion es obligatoria");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        incidents.insert(incident);
        return Result.ok(null);
    }
}
