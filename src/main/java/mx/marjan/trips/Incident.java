package mx.marjan.trips;

import java.time.LocalDate;
import java.time.LocalTime;

public record Incident(
        long id,
        long tripId,
        String tripFolio,
        LocalDate incidentDate,
        LocalTime incidentTime,
        String location,
        IncidentType type,
        String description,
        String actionsTaken) {

    public static Incident empty(long tripId) {
        return new Incident(0, tripId, "", LocalDate.now(), LocalTime.now(),
                "", IncidentType.OTHER, "", "");
    }
}
