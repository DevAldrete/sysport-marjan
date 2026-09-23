package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.clients.PaymentTerms;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.shared.Result;

/** BR-19 / BR-20: invoice creation, derived status and payment limits. */
public final class InvoiceRules {

    private InvoiceRules() {}

    /** BR-20: only delivered or closed requests can be invoiced. */
    public static Result<Void> canInvoice(ServiceRequest request) {
        if (request.status() != RequestStatus.DELIVERED && request.status() != RequestStatus.CLOSED) {
            return Result.err("Solo se puede facturar una solicitud entregada o cerrada");
        }
        if (request.agreedRate() == null || request.agreedRate().signum() <= 0) {
            return Result.err("La solicitud no tiene tarifa acordada");
        }
        return Result.ok(null);
    }

    /** Cash clients are due on issue date; credit clients on issue date + credit days. */
    public static LocalDate dueDate(LocalDate issueDate, PaymentTerms terms, int creditDays) {
        if (terms == PaymentTerms.CREDIT) {
            return issueDate.plusDays(Math.max(creditDays, 0));
        }
        return issueDate;
    }

    /** BR-19: paid when payments cover the amount; overdue when past due and unpaid. */
    public static InvoiceStatus deriveStatus(InvoiceStatus current, BigDecimal amount, BigDecimal paid,
            LocalDate dueDate, LocalDate today) {
        if (current == InvoiceStatus.CANCELLED) {
            return InvoiceStatus.CANCELLED;
        }
        BigDecimal settled = paid == null ? BigDecimal.ZERO : paid;
        if (settled.compareTo(amount) >= 0) {
            return InvoiceStatus.PAID;
        }
        if (dueDate != null && dueDate.isBefore(today)) {
            return InvoiceStatus.OVERDUE;
        }
        return InvoiceStatus.PENDING;
    }

    /** Payments cannot exceed the outstanding balance. */
    public static Result<Void> canPay(BigDecimal amount, BigDecimal alreadyPaid, BigDecimal newPayment) {
        List<String> problems = new ArrayList<>();
        if (newPayment == null || newPayment.signum() <= 0 || !mx.marjan.shared.Validators.isMoney(newPayment)) {
            problems.add("El pago debe ser mayor a cero y dentro del rango permitido");
        } else if (alreadyPaid.add(newPayment).compareTo(amount) > 0) {
            problems.add("El pago excede el saldo pendiente (" + amount.subtract(alreadyPaid) + ")");
        }
        return problems.isEmpty() ? Result.ok(null) : Result.err(problems);
    }
}
