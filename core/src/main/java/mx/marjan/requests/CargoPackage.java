package mx.marjan.requests;

import java.math.BigDecimal;

/**
 * One line of a service request's cargo. A request may carry many packages; the
 * trip (1:1 with the request) inherits the list. Line weight is
 * {@code quantity x unitWeight}. {@code receivedQuantity}/{@code receiptCondition}
 * are filled at delivery for per-unit tracking (null until then).
 */
public record CargoPackage(
        long id,
        long serviceRequestId,
        int lineNo,
        String description,
        BigDecimal quantity,
        PackageUnit unit,
        BigDecimal unitWeight,
        BigDecimal receivedQuantity,
        PackageCondition receiptCondition) {

    /** Weight of the whole line, or null when the per-unit weight is unknown. */
    public BigDecimal totalWeight() {
        return quantity == null || unitWeight == null ? null : quantity.multiply(unitWeight);
    }

    /** Copy carrying a delivery receipt (used by the delivery form). */
    public CargoPackage withReceipt(BigDecimal received, PackageCondition condition) {
        return new CargoPackage(id, serviceRequestId, lineNo, description, quantity, unit,
                unitWeight, received, condition);
    }
}
