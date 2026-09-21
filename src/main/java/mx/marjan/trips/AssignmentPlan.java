package mx.marjan.trips;

import java.time.LocalDateTime;

/** The result of a successful assignment validation. */
public record AssignmentPlan(
        long requestId,
        long vehicleId,
        long employeeId,
        LocalDateTime plannedStart,
        LocalDateTime plannedEnd) {}
