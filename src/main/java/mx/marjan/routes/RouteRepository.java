package mx.marjan.routes;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/**
 * Thin JDBC wrapper over the route stored procedures. A route and its ordered
 * stops are written together in one {@link Database#inTransaction} unit of work,
 * so the list is replaced atomically even though each row is a separate call.
 */
public class RouteRepository {

    private Route map(ResultSet rs) throws SQLException {
        return new Route(
                rs.getLong("id"),
                rs.getString("origin"),
                rs.getString("destination"),
                rs.getBigDecimal("estimated_km"),
                rs.getString("description"),
                rs.getString("route_label"));
    }

    private RouteStop mapStop(ResultSet rs) throws SQLException {
        return new RouteStop(
                rs.getLong("id"),
                rs.getLong("route_id"),
                rs.getInt("sequence_no"),
                rs.getString("location"));
    }

    public List<Route> search(String term) {
        String value = term == null || term.isBlank() ? null : term.trim();
        return Database.callList("{call sp_routes_search(?)}", this::map, value);
    }

    public Optional<Route> findById(long id) {
        return Database.callOne("{call sp_route_by_id(?)}", this::map, id);
    }

    public List<RouteStop> listStops(long routeId) {
        return Database.callList("{call sp_route_stops(?)}", this::mapStop, routeId);
    }

    /** Saves the route header and replaces its stops; the first/last stop set origin/destination. */
    public Result<Long> save(Route route, List<RouteStop> stops) {
        try {
            Long savedId = Database.inTransaction(connection -> {
                String origin = stops.get(0).location();
                String destination = stops.get(stops.size() - 1).location();
                Result<Long> savedRoute = Database.callForId(connection,
                        "{call sp_route_save(?,?,?,?,?,?,?)}", route.id(), origin, destination,
                        route.estimatedKm(), route.description());
                if (savedRoute.isErr()) {
                    throw new RouteRejected(savedRoute.problems());
                }
                long routeId = savedRoute.value();

                Set<Long> kept = new HashSet<>();
                int sequence = 1;
                for (RouteStop stop : stops) {
                    Result<Long> savedStop = Database.callForId(connection,
                            "{call sp_route_stop_save(?,?,?,?,?,?)}", stop.id(), routeId,
                            sequence, stop.location());
                    if (savedStop.isErr()) {
                        throw new RouteRejected(savedStop.problems());
                    }
                    kept.add(savedStop.value());
                    sequence++;
                }
                for (Long existing : listStopIds(connection, routeId)) {
                    if (!kept.contains(existing)) {
                        Result<Void> removed = Database.callVoid(connection,
                                "{call sp_route_stop_delete(?,?)}", existing);
                        if (removed.isErr()) {
                            throw new RouteRejected(removed.problems());
                        }
                    }
                }
                Result<Void> duplicate = Database.callVoid(connection,
                        "{call sp_route_duplicate(?,?)}", routeId);
                if (duplicate.isErr()) {
                    throw new RouteRejected(duplicate.problems());
                }
                return routeId;
            });
            return Result.ok(savedId);
        } catch (RouteRejected rejected) {
            return Result.err(rejected.problems);
        }
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_route_delete(?,?)}", id);
    }

    private List<Long> listStopIds(Connection connection, long routeId) {
        return Database.callList(connection, "{call sp_route_stops(?)}",
                rs -> rs.getLong("id"), routeId);
    }

    /** Signals a business-rule rejection so the surrounding transaction rolls back. */
    private static final class RouteRejected extends RuntimeException {
        private final transient List<String> problems;

        RouteRejected(List<String> problems) {
            super(String.join("; ", problems));
            this.problems = problems;
        }
    }
}
