package mx.marjan.finance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.shared.Result;

/** BR-16: advance settlement. Pure arithmetic over given, expenses and fuel. */
public final class AdvanceRules {

    private AdvanceRules() {}

    public static AdvanceBalance balance(BigDecimal given, BigDecimal expenses, BigDecimal fuel) {
        BigDecimal safeGiven = given == null ? BigDecimal.ZERO : given;
        BigDecimal proven = (expenses == null ? BigDecimal.ZERO : expenses)
                .add(fuel == null ? BigDecimal.ZERO : fuel);
        int sign = safeGiven.compareTo(proven);
        AdvanceBalance.Outcome outcome;
        if (sign > 0) {
            outcome = new AdvanceBalance.OperatorOwes(safeGiven.subtract(proven));
        } else if (sign < 0) {
            outcome = new AdvanceBalance.CompanyOwes(proven.subtract(safeGiven));
        } else {
            outcome = new AdvanceBalance.Settled();
        }
        return new AdvanceBalance(safeGiven, proven, outcome);
    }

    public static List<String> validate(Advance advance) {
        List<String> problems = new ArrayList<>();
        if (advance.tripId() == 0) {
            problems.add("Debe seleccionar un viaje");
        }
        if (advance.employeeId() == 0) {
            problems.add("Debe seleccionar un operador");
        }
        if (advance.amountGiven() == null || advance.amountGiven().signum() <= 0
                || !mx.marjan.shared.Validators.isMoney(advance.amountGiven())) {
            problems.add("El monto del anticipo debe ser mayor a cero y dentro del rango permitido");
        }
        if (advance.deliveredDate() == null) {
            problems.add("La fecha de entrega del anticipo es obligatoria");
        } else if (!mx.marjan.shared.Validators.isValidDate(advance.deliveredDate())) {
            problems.add("La fecha del anticipo no es valida");
        }
        return problems;
    }

    public static Result<Void> validateResult(Advance advance) {
        List<String> problems = validate(advance);
        return problems.isEmpty() ? Result.ok(null) : Result.err(problems);
    }
}
