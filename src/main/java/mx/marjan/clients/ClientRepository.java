package mx.marjan.clients;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

public class ClientRepository {

    private static final String COLUMNS = """
            id, name, rfc, address, phone, email, contact_name,
            client_type, payment_terms, credit_limit, credit_days, status
            """;

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
        if (term == null || term.isBlank()) {
            return Database.queryList("SELECT " + COLUMNS + " FROM clients ORDER BY name", this::map);
        }
        String like = "%" + term.trim() + "%";
        return Database.queryList("SELECT " + COLUMNS + """
                 FROM clients WHERE name LIKE ? OR rfc LIKE ? ORDER BY name
                """, this::map, like, like);
    }

    public List<Client> listActive() {
        return Database.queryList(
                "SELECT " + COLUMNS + " FROM clients WHERE status = 'active' ORDER BY name", this::map);
    }

    public Optional<Client> findById(long id) {
        return Database.queryOne("SELECT " + COLUMNS + " FROM clients WHERE id = ?", this::map, id);
    }

    public long insert(Client client) {
        return Database.insert("""
                INSERT INTO clients
                  (name, rfc, address, phone, email, contact_name, client_type,
                   payment_terms, credit_limit, credit_days, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                client.name(), client.rfc(), client.address(), client.phone(), client.email(),
                client.contactName(), client.clientType().dbValue(), client.paymentTerms().dbValue(),
                client.creditLimit(), client.creditDays(), client.status().dbValue());
    }

    public void update(Client client) {
        Database.update("""
                UPDATE clients SET
                  name = ?, rfc = ?, address = ?, phone = ?, email = ?, contact_name = ?,
                  client_type = ?, payment_terms = ?, credit_limit = ?, credit_days = ?, status = ?
                WHERE id = ?
                """,
                client.name(), client.rfc(), client.address(), client.phone(), client.email(),
                client.contactName(), client.clientType().dbValue(), client.paymentTerms().dbValue(),
                client.creditLimit(), client.creditDays(), client.status().dbValue(), client.id());
    }

    public void setStatus(long id, ClientStatus status) {
        Database.update("UPDATE clients SET status = ? WHERE id = ?", status.dbValue(), id);
    }
}
