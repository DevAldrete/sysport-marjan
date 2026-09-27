package mx.marjan.ui;

import mx.marjan.finance.InvoiceStatus;
import mx.marjan.fleet.VehicleStatus;
import mx.marjan.operators.EmployeeStatus;
import mx.marjan.requests.RequestStatus;
import mx.marjan.trips.TripStatus;

/** The single place that decides which color a status uses, so screens stay consistent. */
public final class StatusTones {

    private StatusTones() {}

    public static StatusBadge.Tone request(RequestStatus status) {
        return switch (status) {
            case REQUESTED -> StatusBadge.Tone.NEUTRAL;
            case AUTHORIZED, SCHEDULED, ASSIGNED -> StatusBadge.Tone.INFO;
            case IN_TRANSIT -> StatusBadge.Tone.WARNING;
            case DELIVERED, CLOSED -> StatusBadge.Tone.SUCCESS;
            case CANCELLED -> StatusBadge.Tone.DANGER;
        };
    }

    public static StatusBadge.Tone trip(TripStatus status) {
        return switch (status) {
            case SCHEDULED -> StatusBadge.Tone.INFO;
            case IN_TRANSIT -> StatusBadge.Tone.WARNING;
            case COMPLETED -> StatusBadge.Tone.SUCCESS;
            case CANCELLED -> StatusBadge.Tone.DANGER;
        };
    }

    public static StatusBadge.Tone vehicle(VehicleStatus status) {
        return switch (status) {
            case AVAILABLE -> StatusBadge.Tone.SUCCESS;
            case ASSIGNED, ON_TRIP -> StatusBadge.Tone.INFO;
            case MAINTENANCE -> StatusBadge.Tone.WARNING;
            case OUT_OF_SERVICE -> StatusBadge.Tone.DANGER;
            case DECOMMISSIONED -> StatusBadge.Tone.NEUTRAL;
        };
    }

    public static StatusBadge.Tone employee(EmployeeStatus status) {
        return switch (status) {
            case AVAILABLE -> StatusBadge.Tone.SUCCESS;
            case ON_TRIP, RESTING -> StatusBadge.Tone.INFO;
            case VACATION -> StatusBadge.Tone.WARNING;
            case INCAPACITATED -> StatusBadge.Tone.DANGER;
            case TERMINATED -> StatusBadge.Tone.NEUTRAL;
        };
    }

    public static StatusBadge.Tone invoice(InvoiceStatus status) {
        return switch (status) {
            case PENDING -> StatusBadge.Tone.INFO;
            case PAID -> StatusBadge.Tone.SUCCESS;
            case OVERDUE -> StatusBadge.Tone.DANGER;
            case CANCELLED -> StatusBadge.Tone.NEUTRAL;
        };
    }
}
