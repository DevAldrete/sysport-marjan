package mx.marjan.trips;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.fleet.Vehicle;
import mx.marjan.operators.Employee;
import mx.marjan.operators.LicenseRules;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.shared.Result;

/**
 * The heart of the system: validates a vehicle/operator assignment.
 * Pure and I/O-free, so every rule can be tested with records (BR-05 ... BR-11).
 * Collects all problems so the UI can show them together.
 */
public final class AssignmentRules {

    private AssignmentRules() {}

    public static Result<AssignmentPlan> validate(
            ServiceRequest request,
            Vehicle vehicle,
            List<Trip> vehicleTripsInWindow,
            Employee operator,
            List<Trip> operatorTripsInWindow,
            LocalDate today) {

        List<String> problems = new ArrayList<>();

        if (request.status() != RequestStatus.SCHEDULED) {
            problems.add("La solicitud debe estar programada para poder asignarle un viaje");
        }
        if (request.pickupScheduled() == null || request.deliveryScheduled() == null) {
            problems.add("La solicitud no tiene fechas programadas");
        }

        // BR-07 / BR-08 / BR-11
        if (vehicle == null) {
            problems.add("Debe seleccionar una unidad");
        } else {
            if (!vehicle.status().isAssignable()) {
                problems.add("La unidad " + vehicle.label() + " no esta disponible ("
                        + vehicle.status().label() + ")");
            }
            if (request.estimatedWeight() != null && vehicle.loadCapacity() != null
                    && vehicle.loadCapacity().compareTo(request.estimatedWeight()) < 0) {
                problems.add("La capacidad de la unidad (" + vehicle.loadCapacity()
                        + " kg) es menor al peso estimado (" + request.estimatedWeight() + " kg)");
            }
            // BR-05
            if (!vehicleTripsInWindow.isEmpty()) {
                problems.add("La unidad " + vehicle.label() + " ya tiene un viaje en ese periodo");
            }
        }

        // BR-09 / BR-10
        if (operator == null) {
            problems.add("Debe seleccionar un operador");
        } else {
            if (!operator.status().isAssignable()) {
                problems.add("El operador " + operator.name() + " no esta disponible ("
                        + operator.status().label() + ")");
            }
            if (request.deliveryScheduled() != null) {
                Result<Void> licenseCheck = LicenseRules.canAssign(
                        operator, request.deliveryScheduled().toLocalDate(), today);
                problems.addAll(licenseCheck.problems());
            }
            // BR-06
            if (!operatorTripsInWindow.isEmpty()) {
                problems.add("El operador " + operator.name() + " ya tiene un viaje en ese periodo");
            }
        }

        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        return Result.ok(new AssignmentPlan(request.id(), vehicle.id(), operator.id(),
                request.pickupScheduled(), request.deliveryScheduled()));
    }

    /** Half-open interval overlap: [aStart, aEnd) and [bStart, bEnd). */
    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
            LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }
}
