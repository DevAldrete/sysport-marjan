package mx.marjan.routes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

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
        if (term == null || term.isBlank()) {
            return Database.queryList("SELECT * FROM routes ORDER BY origin, destination", this::map);
        }
        String like = "%" + term.trim() + "%";
        return Database.queryList(
                "SELECT * FROM routes WHERE origin LIKE ? OR destination LIKE ? ORDER BY origin, destination",
                this::map, like, like);
    }

    public Optional<Route> findById(long id) {
        return Database.queryOne("SELECT * FROM routes WHERE id = ?", this::map, id);
    }

    public long insert(Route route) {
        return Database.insert(
                "INSERT INTO routes (origin, destination, estimated_km, description) VALUES (?, ?, ?, ?)",
                route.origin(), route.destination(), route.estimatedKm(), route.description());
    }

    public void update(Route route) {
        Database.update(
                "UPDATE routes SET origin = ?, destination = ?, estimated_km = ?, description = ? WHERE id = ?",
                route.origin(), route.destination(), route.estimatedKm(), route.description(), route.id());
    }
}
