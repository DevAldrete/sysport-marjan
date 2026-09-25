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

    public ServiceRequest withStatus(RequestStatus newStatus) {
        return new ServiceRequest(id, folio, clientId, clientName, routeId, routeLabel,
                cargoDescription, estimatedWeight, pickupScheduled, deliveryScheduled, agreedRate,
                requiresDocuments, newStatus, notes, createdAt);
    }

    public ServiceRequest withAgreedRate(BigDecimal rate) {
        return new ServiceRequest(id, folio, clientId, clientName, routeId, routeLabel,
                cargoDescription, estimatedWeight, pickupScheduled, deliveryScheduled, rate,
                requiresDocuments, status, notes, createdAt);
    }

    public ServiceRequest withSchedule(LocalDateTime pickup, LocalDateTime delivery) {
        return new ServiceRequest(id, folio, clientId, clientName, routeId, routeLabel,
                cargoDescription, estimatedWeight, pickup, delivery, agreedRate,
                requiresDocuments, status, notes, createdAt);
    }

    public ServiceRequest withNotes(String newNotes) {
        return new ServiceRequest(id, folio, clientId, clientName, routeId, routeLabel,
                cargoDescription, estimatedWeight, pickupScheduled, deliveryScheduled, agreedRate,
                requiresDocuments, status, newNotes, createdAt);
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
