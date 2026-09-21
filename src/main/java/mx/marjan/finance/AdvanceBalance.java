package mx.marjan.finance;

import java.math.BigDecimal;

/** BR-16: given vs proven, and who owes what. Modeled explicitly, no strings. */
public record AdvanceBalance(BigDecimal given, BigDecimal proven, Outcome outcome) {

    public sealed interface Outcome permits Settled, OperatorOwes, CompanyOwes {}

    public record Settled() implements Outcome {}

    public record OperatorOwes(BigDecimal amount) implements Outcome {}

    public record CompanyOwes(BigDecimal amount) implements Outcome {}

    public String label() {
        return switch (outcome) {
            case Settled ignored -> "Comprobado";
            case OperatorOwes owes -> "El operador debe devolver " + owes.amount();
            case CompanyOwes owes -> "La empresa debe reembolsar " + owes.amount();
        };
    }
}
