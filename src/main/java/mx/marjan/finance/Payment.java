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

    public static Payment empty(long invoiceId) {
        return new Payment(0, invoiceId, "", BigDecimal.ZERO, LocalDate.now(), PaymentMethod.CASH);
    }
}
