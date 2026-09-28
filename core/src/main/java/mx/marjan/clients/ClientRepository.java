package mx.marjan.clients;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the client stored procedures. */
public class ClientRepository {

    private Client map(ResultSet rs) throws SQLException {
        return new Client(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("rfc"),
                rs.getString("address"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("contact_name"),
                ClientType.fromDb(rs.getString("client_type")),
                PaymentTerms.fromDb(rs.getString("payment_terms")),
                rs.getBigDecimal("credit_limit"),
                rs.getInt("credit_days"),
                ClientStatus.fromDb(rs.getString("status")));
    }

    public List<Client> search(String term) {
        String value = term == null || term.isBlank() ? null : term.trim();
        return Database.callList("{call sp_clients_search(?)}", this::map, value);
    }

    public List<Client> listActive() {
        return Database.callList("{call sp_clients_active()}", this::map);
    }

    public Optional<Client> findById(long id) {
        return Database.callOne("{call sp_client_by_id(?)}", this::map, id);
    }

    public Result<Long> save(Client client) {
        return Database.callForId("{call sp_client_save(?,?,?,?,?,?,?,?,?,?,?,?,?,?)}",
                client.id(), client.name(), client.rfc(), client.address(), client.phone(),
                client.email(), client.contactName(), client.clientType().dbValue(),
                client.paymentTerms().dbValue(), client.creditLimit(), client.creditDays(),
                client.status().dbValue());
    }

    public Result<Void> setStatus(long id, ClientStatus status) {
        return Database.callVoid("{call sp_client_set_status(?,?,?)}", id, status.dbValue());
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_client_delete(?,?)}", id);
    }
}
