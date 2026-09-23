package mx.marjan.routes;

import java.util.ArrayList;
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

    public Result<Route> save(Route route) {
        if (!Session.has(Permissions.ROUTES_WRITE)) {
            return Result.err("No tiene permiso para modificar rutas");
        }
        List<String> problems = new ArrayList<>();
        if (route.origin() == null || route.origin().isBlank()) {
            problems.add("El origen es obligatorio");
        }
        if (route.destination() == null || route.destination().isBlank()) {
            problems.add("El destino es obligatorio");
        }
        if (!mx.marjan.shared.Validators.isMeasure(route.estimatedKm())) {
            problems.add("Los kilometros estimados son invalidos o exceden el maximo permitido");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        if (route.id() == 0) {
            Long id = mx.marjan.shared.Database.inTransaction(connection -> routes.insert(connection, route));
            return Result.ok(new Route(id, route.origin(), route.destination(),
                    route.estimatedKm(), route.description()));
        }
        routes.update(route);
        return Result.ok(route);
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.ROUTES_WRITE)) {
            return Result.err("No tiene permiso para eliminar rutas");
        }
        routes.delete(id);
        return Result.ok(null);
    }
}
