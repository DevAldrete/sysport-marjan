package mx.marjan.trips;

import java.time.LocalDateTime;

public record Delivery(
        long id,
        long tripId,
        String tripFolio,
        LocalDateTime actualDatetime,
        String receivedBy,
        String evidenceReference,
        DeliveryStatus status) {

    public static Delivery empty(long tripId) {
        return new Delivery(0, tripId, "", LocalDateTime.now(), "", "", DeliveryStatus.PENDING_DOCUMENTS);
    }
}
