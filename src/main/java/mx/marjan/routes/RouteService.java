package mx.marjan.routes;

import java.util.List;
import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class RouteService {

    private final RouteRepository routes = new RouteRepository();

    public List<Route> search(String term) {
        return routes.search(term);
    }

    public List<Route> listAll() {
        return routes.search(null);
    }

    public Optional<Route> find(long id) {
        return routes.findById(id);
    }

    public List<RouteStop> stops(long routeId) {
        return routes.listStops(routeId);
    }

    public Result<Route> save(Route route, List<RouteStop> stops) {
        if (!Session.has(Permissions.ROUTES_WRITE)) {
            return Result.err("No tiene permiso para modificar rutas");
        }
        if (stops == null || stops.size() < 2) {
            return Result.err("Una ruta debe tener al menos un origen y un destino");
        }
        for (RouteStop stop : stops) {
            if (stop.location() == null || stop.location().isBlank()) {
                return Result.err("Todas las paradas deben tener un nombre");
            }
        }
        Result<Long> saved = routes.save(route, stops);
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return routes.findById(saved.value()).map(Result::ok).orElse(Result.err("Ruta no encontrada"));
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.ROUTES_WRITE)) {
            return Result.err("No tiene permiso para eliminar rutas");
        }
        return routes.delete(id);
    }
}
