package mx.marjan.routes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the route stored procedures. */
public class RouteRepository {

    private Route map(ResultSet rs) throws SQLException {
        return new Route(
                rs.getLong("id"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getBigDecimal("estimated_km"),
                rs.getString("description"));
    }

    public List<Route> search(String term) {
        String value = term == null || term.isBlank() ? null : term.trim();
        return Database.callList("{call sp_routes_search(?)}", this::map, value);
    }

    public Optional<Route> findById(long id) {
        return Database.callOne("{call sp_route_by_id(?)}", this::map, id);
    }

    public Result<Long> save(Route route) {
        return Database.callForId("{call sp_route_save(?,?,?,?,?,?,?)}",
                route.id(), route.origin(), route.destination(), route.estimatedKm(),
                route.description());
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_route_delete(?,?)}", id);
    }
}
