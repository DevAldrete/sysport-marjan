package mx.marjan.finance;

import java.util.List;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.security.SessionCaller;
import mx.marjan.shared.Result;

public class ExpenseService {

    private final ExpenseRepository expenses = new ExpenseRepository();
    private final Caller caller;

    public ExpenseService() {
        this(SessionCaller.INSTANCE);
    }

    public ExpenseService(Caller caller) {
        this.caller = caller;
    }

    public List<Expense> listByTrip(long tripId) {
        return expenses.listByTrip(tripId);
    }

    public Result<Void> register(Expense expense) {
        if (!caller.has(Permissions.EXPENSES_WRITE)) {
            return Result.err("No tiene permiso para registrar gastos");
        }
        Result<Long> saved = expenses.save(expense, caller.userId());
        return saved.isOk() ? Result.ok(null) : Result.err(saved.problems());
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.EXPENSES_WRITE)) {
            return Result.err("No tiene permiso para eliminar gastos");
        }
        expenses.delete(id);
        return Result.ok(null);
    }
}
