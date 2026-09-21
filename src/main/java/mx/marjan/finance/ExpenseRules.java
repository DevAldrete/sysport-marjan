package mx.marjan.finance;

import java.util.ArrayList;
import java.util.List;
import mx.marjan.shared.Result;

/** BR-17: expense type must be allowed and amount must be positive. */
public final class ExpenseRules {

    private ExpenseRules() {}

    public static Result<Void> validate(Expense expense) {
        List<String> problems = new ArrayList<>();
        if (expense.tripId() == 0) {
            problems.add("Debe seleccionar un viaje");
        }
        if (expense.type() == null) {
            problems.add("Debe seleccionar un tipo de gasto");
        }
        if (expense.amount() == null || expense.amount().signum() <= 0) {
            problems.add("El importe debe ser mayor a cero");
        }
        if (expense.expenseDate() == null) {
            problems.add("La fecha del gasto es obligatoria");
        }
        return problems.isEmpty() ? Result.ok(null) : Result.err(problems);
    }
}
