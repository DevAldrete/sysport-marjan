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
        if (!mx.marjan.shared.Validators.isValidDate(incident.incidentDate())) {
            problems.add("La fecha de la incidencia no es valida");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        mx.marjan.shared.Database.inTransaction(connection -> {
            incidents.insert(connection, incident);
            return null;
        });
        return Result.ok(null);
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.INCIDENTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar incidencias");
        }
        incidents.delete(id);
        return Result.ok(null);
    }
}
