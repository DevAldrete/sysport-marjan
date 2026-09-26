package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.clients.ClientRepository;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

public class InvoiceService {

    private final InvoiceRepository invoices = new InvoiceRepository();
    private final PaymentRepository payments = new PaymentRepository();
    private final ClientRepository clients = new ClientRepository();

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
        Result<Void> canInvoice = InvoiceRules.canInvoice(request);
        if (canInvoice.isErr()) {
            return Result.err(canInvoice.problems());
        }
        if (invoices.findByRequest(request.id()).isPresent()) {
            return Result.err("La solicitud ya tiene una factura");
        }
        BigDecimal finalAmount = amount != null ? amount : request.agreedRate();
        if (finalAmount == null || finalAmount.signum() <= 0
                || !mx.marjan.shared.Validators.isMoney(finalAmount)) {
            return Result.err("El importe de la factura debe ser mayor a cero y dentro del rango permitido");
        }
        Optional<mx.marjan.clients.Client> client = clients.findById(request.clientId());
        if (client.isEmpty()) {
            return Result.err("Cliente no encontrado");
        }
        LocalDate issue = issueDate != null ? issueDate : LocalDate.now();
        if (!mx.marjan.shared.Validators.isValidDate(issue)) {
            return Result.err("La fecha de emision no es valida");
        }
        LocalDate due = InvoiceRules.dueDate(issue, client.get().paymentTerms(), client.get().creditDays());
        if (due.isBefore(issue)) {
            return Result.err("La fecha de vencimiento no puede ser anterior a la de emision");
        }
        String number = String.format("INV-%d-%06d", issue.getYear(), invoices.nextSequence(issue.getYear()));
        Invoice invoice = new Invoice(0, request.clientId(), client.get().name(), request.id(),
                request.folio(), number, finalAmount, issue, due, InvoiceStatus.PENDING, BigDecimal.ZERO);
        long userId = Session.userId();
        Long id = Database.inTransaction(connection -> invoices.insert(connection, invoice, userId));
        return Result.ok(new Invoice(id, invoice.clientId(), invoice.clientName(),
                invoice.serviceRequestId(), invoice.requestFolio(), invoice.invoiceNumber(),
                invoice.amount(), invoice.issueDate(), invoice.dueDate(), invoice.status(),
                invoice.paid()));
    }

    /** BR-19: partial payments allowed, overpayment rejected, status re-derived. */
    public Result<Void> registerPayment(long invoiceId, BigDecimal amount, LocalDate date, PaymentMethod method) {
        if (!Session.has(Permissions.PAYMENTS_WRITE)) {
            return Result.err("No tiene permiso para registrar pagos");
        }
        Optional<Invoice> found = invoices.findById(invoiceId);
        if (found.isEmpty()) {
            return Result.err("Factura no encontrada");
        }
        Invoice invoice = found.get();
        Result<Void> canPay = InvoiceRules.canPay(invoice.amount(), invoice.paid(),
                amount == null ? BigDecimal.ZERO : amount);
        if (canPay.isErr()) {
            return Result.err(canPay.problems());
        }
        LocalDate paymentDate = date != null ? date : LocalDate.now();
        PaymentMethod paymentMethod = method != null ? method : PaymentMethod.CASH;
        Payment payment = new Payment(0, invoiceId, invoice.invoiceNumber(), amount, paymentDate, paymentMethod);
        long userId = Session.userId();
        Database.inTransaction(connection -> {
            payments.insert(connection, payment, userId);
            BigDecimal paid = payments.sumByInvoice(connection, invoiceId);
            InvoiceStatus status = InvoiceRules.deriveStatus(invoice.status(), invoice.amount(),
                    paid, invoice.dueDate(), LocalDate.now());
            invoices.updateStatus(connection, invoiceId, status);
            return null;
        });
        return Result.ok(null);
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.INVOICES_WRITE)) {
            return Result.err("No tiene permiso para eliminar facturas");
        }
        Database.inTransaction(connection -> {
            invoices.deleteCascade(connection, id);
            return null;
        });
        return Result.ok(null);
    }

    public Result<Void> deletePayment(long id) {
        if (!Session.has(Permissions.PAYMENTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar pagos");
        }
        payments.delete(id);
        return Result.ok(null);
    }

    /** BR-19 / FR-INV-3: recomputes paid/overdue for every open invoice. Returns how many changed. */
    public int refreshStatuses(LocalDate today) {
        int changed = 0;
        for (Invoice invoice : invoices.search(null, null)) {
            if (invoice.status() == InvoiceStatus.CANCELLED) {
                continue;
            }
            InvoiceStatus derived = InvoiceRules.deriveStatus(invoice.status(), invoice.amount(),
                    invoice.paid(), invoice.dueDate(), today);
            if (derived != invoice.status()) {
                invoices.updateStatus(invoice.id(), derived);
                changed++;
            }
        }
        return changed;
    }
}
