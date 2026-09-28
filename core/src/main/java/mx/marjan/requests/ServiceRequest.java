package mx.marjan.requests;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ServiceRequest(
        long id,
        String folio,
        long clientId,
        String clientName,
        long routeId,
        String routeLabel,
        String cargoDescription,
        BigDecimal estimatedWeight,
        int packageCount,
        BigDecimal packageWeight,
        LocalDateTime pickupScheduled,
        LocalDateTime deliveryScheduled,
        BigDecimal agreedRate,
        boolean requiresDocuments,
        RequestStatus status,
        String notes,
        LocalDateTime createdAt) {

    /**
     * Cargo weight to display and validate: the sum of the packages when the
     * request has any, else the manually estimated weight (BR-08 uses the same
     * value in the database through {@code fn_request_weight}).
     */
    public BigDecimal effectiveWeight() {
        return packageWeight != null ? packageWeight : estimatedWeight;
    }

    /** Compact one-line label for lists and combo boxes; never dumps the whole record. */
    public String label() {
        return folio + " - " + clientName;
    }

    @Override
    public String toString() {
        return label();
    }
}
