package mx.marjan.trips;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Trip(
        long id,
        long serviceRequestId,
        String folio,
        String clientName,
        String routeLabel,
        long vehicleId,
        String vehicleLabel,
        long employeeId,
        String employeeName,
        BigDecimal estimatedKm,
        BigDecimal actualKm,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd,
        LocalDateTime departure,
        LocalDateTime arrival,
        TripStatus status) {

    public boolean isActive() {
        return status.isActive();
    }

    public Trip withStatus(TripStatus newStatus) {
        return new Trip(id, serviceRequestId, folio, clientName, routeLabel, vehicleId, vehicleLabel,
                employeeId, employeeName, estimatedKm, actualKm, plannedStart, plannedEnd,
                departure, arrival, newStatus);
    }

    @Override
    public String toString() {
        return folio + " - " + vehicleLabel;
    }
}
