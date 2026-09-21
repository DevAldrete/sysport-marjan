package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Expense(
        long id,
        long tripId,
        String tripFolio,
        ExpenseType type,
        BigDecimal amount,
        LocalDate expenseDate,
        String description) {

    public static Expense empty(long tripId) {
        return new Expense(0, tripId, "", ExpenseType.TOLLS, BigDecimal.ZERO, LocalDate.now(), "");
    }
}
