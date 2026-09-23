package mx.marjan.clients;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import mx.marjan.shared.Database;

public class ClientRateRepository {

    private static final String BASE = """
            SELECT cr.id, cr.client_id, cr.route_id,
                   CONCAT(r.origin, ' -> ', r.destination) AS route_label,
                   cr.rate, cr.valid_from, cr.valid_to
            FROM client_rates cr
            JOIN routes r ON r.id = cr.route_id
            """;

    private ClientRate map(ResultSet rs) throws SQLException {
        return new ClientRate(
                rs.getLong("id"),
                rs.getLong("client_id"),
                rs.getLong("route_id"),
                rs.getString("route_label"),
                rs.getBigDecimal("rate"),
                rs.getObject("valid_from", java.time.LocalDate.class),
                rs.getObject("valid_to", java.time.LocalDate.class));
    }

    public List<ClientRate> listByClient(long clientId) {
        return Database.queryList(BASE + " WHERE cr.client_id = ? ORDER BY r.origin, r.destination",
                this::map, clientId);
    }

    public long insert(java.sql.Connection connection, ClientRate rate) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "client_rates");
        Database.update(connection, """
                INSERT INTO client_rates (id, client_id, route_id, rate, valid_from, valid_to)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, rate.clientId(), rate.routeId(), rate.rate(), rate.validFrom(), rate.validTo());
        return id;
    }

    public void update(ClientRate rate) {
        Database.update("""
                UPDATE client_rates SET route_id = ?, rate = ?, valid_from = ?, valid_to = ?
                WHERE id = ?
                """, rate.routeId(), rate.rate(), rate.validFrom(), rate.validTo(), rate.id());
    }

    public void delete(long id) {
        Database.update("DELETE FROM client_rates WHERE id = ?", id);
    }
}
