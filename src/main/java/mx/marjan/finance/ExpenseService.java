package mx.marjan.finance;

import java.util.List;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

public class ExpenseService {

    private final ExpenseRepository expenses = new ExpenseRepository();

    public List<Expense> listByTrip(long tripId) {
        return expenses.listByTrip(tripId);
    }

    public Result<Void> register(Expense expense) {
        if (!Session.has(Permissions.EXPENSES_WRITE)) {
            return Result.err("No tiene permiso para registrar gastos");
        }
        Result<Void> validated = ExpenseRules.validate(expense);
        if (validated.isErr()) {
            return validated;
        }
        long userId = Session.userId();
        Database.inTransaction(connection -> {
            expenses.insert(connection, expense, userId);
            return null;
        });
        return Result.ok(null);
    }
}
