package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Payment(
        long id,
        long invoiceId,
        String invoiceNumber,
        BigDecimal amount,
        LocalDate paymentDate,
        PaymentMethod method) {
}
