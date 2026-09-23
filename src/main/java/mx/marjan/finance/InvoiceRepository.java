package mx.marjan.finance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

public class InvoiceRepository {

    private static final String BASE = """
            SELECT i.id, i.client_id, c.name AS client_name, i.service_request_id, sr.folio,
                   i.invoice_number, i.amount, i.issue_date, i.due_date, i.status,
                   COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0) AS paid
            FROM invoices i
            JOIN clients c ON c.id = i.client_id
            JOIN service_requests sr ON sr.id = i.service_request_id
            """;

    private Invoice map(ResultSet rs) throws SQLException {
        return new Invoice(
                rs.getLong("id"),
                rs.getLong("client_id"),
                rs.getString("client_name"),
                rs.getLong("service_request_id"),
                rs.getString("folio"),
                rs.getString("invoice_number"),
                rs.getBigDecimal("amount"),
                rs.getObject("issue_date", LocalDate.class),
                rs.getObject("due_date", LocalDate.class),
                InvoiceStatus.fromDb(rs.getString("status")),
                rs.getBigDecimal("paid"));
    }

    public List<Invoice> search(InvoiceStatus status, Long clientId) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (status != null) {
            conditions.add("i.status = ?");
            params.add(status.dbValue());
        }
        if (clientId != null) {
            conditions.add("i.client_id = ?");
            params.add(clientId);
        }
        String sql = BASE + (conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions))
                + " ORDER BY i.issue_date DESC";
        return Database.queryList(sql, this::map, params.toArray());
    }

    public Optional<Invoice> findById(long id) {
        return Database.queryOne(BASE + " WHERE i.id = ?", this::map, id);
    }

    public Optional<Invoice> findByRequest(long serviceRequestId) {
        return Database.queryOne(BASE + " WHERE i.service_request_id = ?", this::map, serviceRequestId);
    }

    public long nextSequence(int year) {
        return Database.queryOne("""
                SELECT COALESCE(MAX(CAST(SUBSTRING(invoice_number, 10) AS UNSIGNED)), 0) + 1 AS next_seq
                FROM invoices WHERE invoice_number LIKE ?
                """, rs -> rs.getLong("next_seq"), "INV-" + year + "-%").orElse(1L);
    }

    public long insert(Connection connection, Invoice invoice, long userId) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "invoices");
        Database.update(connection, """
                INSERT INTO invoices
                  (id, client_id, service_request_id, invoice_number, amount, issue_date, due_date,
                   status, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, invoice.clientId(), invoice.serviceRequestId(), invoice.invoiceNumber(),
                invoice.amount(), invoice.issueDate(), invoice.dueDate(),
                invoice.status().dbValue(), userId);
        return id;
    }

    public void delete(long id) {
        Database.update("DELETE FROM invoices WHERE id = ?", id);
    }

    public void updateStatus(long id, InvoiceStatus status) {
        Database.update("UPDATE invoices SET status = ? WHERE id = ?", status.dbValue(), id);
    }

    public void updateStatus(Connection connection, long id, InvoiceStatus status) throws SQLException {
        Database.update(connection, "UPDATE invoices SET status = ? WHERE id = ?", status.dbValue(), id);
    }
}
