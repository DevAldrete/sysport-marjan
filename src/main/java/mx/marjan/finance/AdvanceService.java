package mx.marjan.finance;

import java.util.List;
import mx.marjan.fleet.FuelLoadRepository;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

public class AdvanceService {

    private final AdvanceRepository advances = new AdvanceRepository();
    private final ExpenseRepository expenses = new ExpenseRepository();
    private final FuelLoadRepository fuels = new FuelLoadRepository();

    public List<Advance> listByTrip(long tripId) {
        return advances.listByTrip(tripId);
    }

    public Result<Void> register(Advance advance) {
        if (!Session.has(Permissions.ADVANCES_WRITE)) {
            return Result.err("No tiene permiso para registrar anticipos");
        }
        Result<Void> validated = AdvanceRules.validateResult(advance);
        if (validated.isErr()) {
            return validated;
        }
        long userId = Session.userId();
        Database.inTransaction(connection -> {
            advances.insert(connection, advance, userId);
            return null;
        });
        return Result.ok(null);
    }

    public Result<Void> settle(long advanceId, long tripId) {
        if (!Session.has(Permissions.ADVANCES_WRITE)) {
            return Result.err("No tiene permiso para comprobar anticipos");
        }
        long userId = Session.userId();
        Database.inTransaction(connection -> {
            advances.markSettled(connection, advanceId, userId);
            return null;
        });
        return Result.ok(null);
    }

    /** BR-16: compares the trip's advance against proven expenses plus fuel. */
    public AdvanceBalance balanceForTrip(long tripId) {
        java.math.BigDecimal given = advances.listByTrip(tripId).stream()
                .map(Advance::amountGiven)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return AdvanceRules.balance(given, expenses.sumByTrip(tripId), fuels.sumByTrip(tripId));
    }

    public java.math.BigDecimal totalExpenses(long tripId) {
        return expenses.sumByTrip(tripId);
    }

    public java.math.BigDecimal totalFuel(long tripId) {
        return fuels.sumByTrip(tripId);
    }
}
