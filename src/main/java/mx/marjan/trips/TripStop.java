package mx.marjan.trips;

import java.time.LocalDateTime;

/**
 * A planned stop of a trip's route together with the actual arrival, when it has
 * been recorded. {@code arrivalId} is null until then.
 */
public record TripStop(long routeStopId, int sequenceNo, String location,
        Long arrivalId, LocalDateTime arrivedAt, String notes) {

    public boolean visited() {
        return arrivedAt != null;
    }
}
