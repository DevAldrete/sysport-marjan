package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record Advance(
        long id,
        long tripId,
        String tripFolio,
        long employeeId,
        String employeeName,
        BigDecimal amountGiven,
        LocalDate deliveredDate,
        AdvanceStatus status,
        LocalDateTime settledAt) {

    public static Advance empty(long tripId, long employeeId) {
        return new Advance(0, tripId, "", employeeId, "", BigDecimal.ZERO,
                LocalDate.now(), AdvanceStatus.PENDING, null);
    }
}
