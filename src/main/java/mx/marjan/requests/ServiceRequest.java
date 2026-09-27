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
        LocalDateTime pickupScheduled,
        LocalDateTime deliveryScheduled,
        BigDecimal agreedRate,
        boolean requiresDocuments,
        RequestStatus status,
        String notes,
        LocalDateTime createdAt) {

    /** Compact one-line label for lists and combo boxes; never dumps the whole record. */
    public String label() {
        return folio + " - " + clientName;
    }

    @Override
    public String toString() {
        return label();
    }
}
