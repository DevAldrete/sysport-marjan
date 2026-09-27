package mx.marjan.finance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.shared.Database;

/** Thin JDBC wrapper over the payment stored procedures. */
public class PaymentRepository {

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
        return Database.callList("{call sp_payments_by_invoice(?)}", this::map, invoiceId);
    }

    public List<Payment> listAll() {
        return Database.callList("{call sp_payments_list()}", this::map);
    }

    public BigDecimal sumByInvoice(long invoiceId) {
        return Database.callOne("{call sp_payment_sum_by_invoice(?)}",
                rs -> rs.getBigDecimal("total"), invoiceId).orElse(BigDecimal.ZERO);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_payment_delete(?)}", id);
    }
}
