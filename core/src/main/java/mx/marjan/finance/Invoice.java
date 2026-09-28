package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Invoice(
        long id,
        long clientId,
        String clientName,
        long serviceRequestId,
        String requestFolio,
        String invoiceNumber,
        BigDecimal amount,
        LocalDate issueDate,
        LocalDate dueDate,
        InvoiceStatus status,
        BigDecimal paid) {

    public BigDecimal balance() {
        return amount.subtract(paid == null ? BigDecimal.ZERO : paid);
    }
}
