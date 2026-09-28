package mx.marjan.finance;

import java.util.List;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.shared.Result;

public class AdvanceService {

    private final AdvanceRepository advances = new AdvanceRepository();
    private final Caller caller;

    public AdvanceService(Caller caller) {
        this.caller = caller;
    }

    public List<Advance> listByTrip(long tripId) {
        return advances.listByTrip(tripId);
    }

    public Result<Void> register(Advance advance) {
        if (!caller.has(Permissions.ADVANCES_WRITE)) {
            return Result.err("No tiene permiso para registrar anticipos");
        }
        Result<Long> saved = advances.save(advance, caller.userId());
        return saved.isOk() ? Result.ok(null) : Result.err(saved.problems());
    }

    public Result<Void> settle(long advanceId) {
        if (!caller.has(Permissions.ADVANCES_WRITE)) {
            return Result.err("No tiene permiso para comprobar anticipos");
        }
        return advances.settle(advanceId, caller.userId());
    }

    /** BR-16: compares the trip's advance against proven expenses plus fuel. */
    public AdvanceBalance balanceForTrip(long tripId) {
        return advances.balance(tripId);
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.ADVANCES_WRITE)) {
            return Result.err("No tiene permiso para eliminar anticipos");
        }
        advances.delete(id);
        return Result.ok(null);
    }
}
