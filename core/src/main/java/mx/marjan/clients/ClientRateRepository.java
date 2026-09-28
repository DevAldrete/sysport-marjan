package mx.marjan.clients;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the client-rate stored procedures. */
public class ClientRateRepository {

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
        return Database.callList("{call sp_client_rates_by_client(?)}", this::map, clientId);
    }

    /** BR-04: newest rate valid for the route on the given date. */
    public Optional<BigDecimal> suggestRate(long clientId, long routeId, LocalDate date) {
        return Database.callOne("{call sp_client_rate_suggest(?,?,?)}",
                rs -> rs.getBigDecimal("rate"), clientId, routeId, date);
    }

    public Result<Long> save(ClientRate rate) {
        return Database.callForId("{call sp_client_rate_save(?,?,?,?,?,?,?,?)}",
                rate.id(), rate.clientId(), rate.routeId(), rate.rate(),
                rate.validFrom(), rate.validTo());
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_client_rate_delete(?,?)}", id);
    }
}
