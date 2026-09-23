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
        if (expense.amount() == null || expense.amount().signum() <= 0
                || !mx.marjan.shared.Validators.isMoney(expense.amount())) {
            problems.add("El importe debe ser mayor a cero y dentro del rango permitido");
        }
        if (expense.expenseDate() == null) {
            problems.add("La fecha del gasto es obligatoria");
        } else if (!mx.marjan.shared.Validators.isValidDate(expense.expenseDate())) {
            problems.add("La fecha del gasto no es valida");
        }
        return problems.isEmpty() ? Result.ok(null) : Result.err(problems);
    }
}
