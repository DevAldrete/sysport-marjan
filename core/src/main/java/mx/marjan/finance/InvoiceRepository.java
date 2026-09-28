package mx.marjan.finance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the invoice stored procedures. */
public class InvoiceRepository {

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
        return Database.callList("{call sp_invoices_search(?,?)}",
                this::map, status == null ? null : status.dbValue(), clientId);
    }

    public Optional<Invoice> findById(long id) {
        return Database.callOne("{call sp_invoice_by_id(?)}", this::map, id);
    }

    public Optional<Invoice> findByRequest(long serviceRequestId) {
        return Database.callOne("{call sp_invoice_by_request(?)}", this::map, serviceRequestId);
    }

    /** BR-20: one invoice per delivered/closed request; due date from payment terms. */
    public Result<Long> createFromRequest(long requestId, LocalDate issueDate, BigDecimal amount,
            long userId) {
        return Database.callForProblemsAndId("{call sp_create_invoice_from_request(?,?,?,?,?,?)}",
                requestId, issueDate, amount, userId);
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_invoice_delete(?,?)}", id);
    }

    /** BR-19: cancel a pending/overdue invoice that has no payments yet. */
    public Result<Void> cancel(long id) {
        return Database.callVoid("{call sp_cancel_invoice(?,?)}", id);
    }

    /** BR-19 / FR-INV-3: recompute paid/overdue for every open invoice. Returns how many changed. */
    public int refreshStatuses(LocalDate today) {
        Object[] out = Database.call("{call sp_refresh_invoice_statuses(?,?)}",
                new int[] { Types.INTEGER }, today);
        return out[0] == null ? 0 : ((Number) out[0]).intValue();
    }

    /** BR-19: register a payment and re-derive the invoice status, in one transaction. */
    public Result<Void> registerPayment(long invoiceId, BigDecimal amount, LocalDate date,
            String method, long userId) {
        return Database.callVoid("{call sp_register_payment(?,?,?,?,?,?)}",
                invoiceId, amount, date, method, userId);
    }
}
