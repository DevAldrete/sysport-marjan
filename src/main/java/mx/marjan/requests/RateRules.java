package mx.marjan.requests;

import java.math.BigDecimal;
import mx.marjan.shared.Result;

/** BR-04: the agreed rate is a positive snapshot taken at authorization. */
public final class RateRules {

    private RateRules() {}

    public static Result<Void> validateAgreedRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            return Result.err("La tarifa acordada debe ser mayor a cero");
        }
        if (!mx.marjan.shared.Validators.isMoney(rate)) {
            return Result.err("La tarifa acordada excede el maximo permitido");
        }
        return Result.ok(null);
    }
}
