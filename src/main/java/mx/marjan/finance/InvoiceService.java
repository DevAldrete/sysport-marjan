package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class InvoiceService {

    private final InvoiceRepository invoices = new InvoiceRepository();
    private final PaymentRepository payments = new PaymentRepository();

    public List<Invoice> search(InvoiceStatus status, Long clientId) {
        return invoices.search(status, clientId);
    }

    public Optional<Invoice> find(long id) {
        return invoices.findById(id);
    }

    public Optional<Invoice> findByRequest(long serviceRequestId) {
        return invoices.findByRequest(serviceRequestId);
    }

    public List<Payment> paymentsFor(long invoiceId) {
        return payments.listByInvoice(invoiceId);
    }

    /** BR-20: one invoice per delivered/closed request; amount defaults to the agreed rate. */
    public Result<Invoice> createFromRequest(ServiceRequest request, LocalDate issueDate, BigDecimal amount) {
        if (!Session.has(Permissions.INVOICES_WRITE)) {
            return Result.err("No tiene permiso para crear facturas");
        }
        Result<Long> saved = invoices.createFromRequest(request.id(), issueDate, amount, Session.userId());
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return invoices.findById(saved.value()).map(Result::ok).orElse(Result.err("Factura no encontrada"));
    }

    /** BR-19: partial payments allowed, overpayment rejected, status re-derived. */
    public Result<Void> registerPayment(long invoiceId, BigDecimal amount, LocalDate date,
            PaymentMethod method) {
        if (!Session.has(Permissions.PAYMENTS_WRITE)) {
            return Result.err("No tiene permiso para registrar pagos");
        }
        PaymentMethod paymentMethod = method != null ? method : PaymentMethod.CASH;
        return invoices.registerPayment(invoiceId, amount, date, paymentMethod.dbValue(), Session.userId());
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.INVOICES_WRITE)) {
            return Result.err("No tiene permiso para eliminar facturas");
        }
        return invoices.delete(id);
    }

    /** BR-19: cancel a pending/overdue invoice that has no payments yet. */
    public Result<Void> cancel(long id) {
        if (!Session.has(Permissions.INVOICES_WRITE)) {
            return Result.err("No tiene permiso para cancelar facturas");
        }
        return invoices.cancel(id);
    }

    public Result<Void> deletePayment(long id) {
        if (!Session.has(Permissions.PAYMENTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar pagos");
        }
        return payments.delete(id);
    }

    /** BR-19 / FR-INV-3: recomputes paid/overdue for every open invoice. Returns how many changed. */
    public int refreshStatuses(LocalDate today) {
        if (!Session.has(Permissions.INVOICES_WRITE)) {
            return 0;
        }
        return invoices.refreshStatuses(today);
    }
}
