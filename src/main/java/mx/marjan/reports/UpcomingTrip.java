package mx.marjan.reports;

import java.time.LocalDateTime;
import mx.marjan.trips.TripStatus;

/** FR-DSH-3: one row of the "next departures" list. */
public record UpcomingTrip(
        long id,
        String folio,
        String clientName,
        String routeLabel,
        String vehicleLabel,
        String operatorName,
        LocalDateTime plannedStart,
        TripStatus status) {}
