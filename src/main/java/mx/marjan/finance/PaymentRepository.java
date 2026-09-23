package mx.marjan.finance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.shared.Database;

public class PaymentRepository {

    private static final String BASE = """
            SELECT p.id, p.invoice_id, i.invoice_number, p.amount, p.payment_date, p.payment_method
            FROM payments p
            JOIN invoices i ON i.id = p.invoice_id
            """;

    private Payment map(ResultSet rs) throws SQLException {
        return new Payment(
                rs.getLong("id"),
                rs.getLong("invoice_id"),
                rs.getString("invoice_number"),
                rs.getBigDecimal("amount"),
                rs.getObject("payment_date", LocalDate.class),
                PaymentMethod.fromDb(rs.getString("payment_method")));
    }

    public List<Payment> listByInvoice(long invoiceId) {
        return Database.queryList(BASE + " WHERE p.invoice_id = ? ORDER BY p.payment_date", this::map, invoiceId);
    }

    public List<Payment> listAll() {
        return Database.queryList(BASE + " ORDER BY p.payment_date DESC", this::map);
    }

    public BigDecimal sumByInvoice(Connection connection, long invoiceId) throws SQLException {
        return Database.queryOne(connection,
                "SELECT COALESCE(SUM(amount), 0) AS total FROM payments WHERE invoice_id = ?",
                rs -> rs.getBigDecimal("total"), invoiceId).orElse(BigDecimal.ZERO);
    }

    public void insert(Connection connection, Payment payment, long userId) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "payments");
        Database.update(connection, """
                INSERT INTO payments (id, invoice_id, amount, payment_date, payment_method, created_by)
                VALUES (?, ?, ?, ?, ?, ?)
                """, id, payment.invoiceId(), payment.amount(), payment.paymentDate(),
                payment.method().dbValue(), userId);
    }

    public void delete(long id) {
        Database.update("DELETE FROM payments WHERE id = ?", id);
    }
}
