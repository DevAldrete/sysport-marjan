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
}
