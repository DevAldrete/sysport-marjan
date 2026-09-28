package mx.marjan.routes;

import java.util.List;
import java.util.Optional;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.security.SessionCaller;
import mx.marjan.shared.Result;

public class RouteService {

    private final RouteRepository routes = new RouteRepository();
    private final Caller caller;

    public RouteService() {
        this(SessionCaller.INSTANCE);
    }

    public RouteService(Caller caller) {
        this.caller = caller;
    }

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
        if (!caller.has(Permissions.ROUTES_WRITE)) {
            return Result.err("No tiene permiso para modificar rutas");
        }
        Result<Long> saved = routes.save(route);
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return Result.ok(new Route(saved.value(), route.origin(), route.destination(),
                route.estimatedKm(), route.description()));
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.ROUTES_WRITE)) {
            return Result.err("No tiene permiso para eliminar rutas");
        }
        return routes.delete(id);
    }
}
