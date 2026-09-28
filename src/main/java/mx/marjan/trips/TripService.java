package mx.marjan.trips;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mx.marjan.fleet.Vehicle;
import mx.marjan.fleet.VehicleRepository;
import mx.marjan.operators.Employee;
import mx.marjan.operators.EmployeeRepository;
import mx.marjan.requests.ServiceRequestRepository;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

/**
 * Trip assignment and execution. Every operation is delegated to a stored
 * procedure that validates, locks and writes in a single database transaction.
 */
public class TripService {

    private final TripRepository trips = new TripRepository();
    private final TripStopRepository tripStops = new TripStopRepository();
    private final VehicleRepository vehicles = new VehicleRepository();
    private final EmployeeRepository employees = new EmployeeRepository();
    private final ServiceRequestRepository requests = new ServiceRequestRepository();

    public List<Trip> search(String term, TripStatus status) {
        return trips.search(term, status);
    }

    public Optional<Trip> find(long id) {
        return trips.findById(id);
    }

    public Optional<Trip> findByRequest(long serviceRequestId) {
        return trips.findByServiceRequest(serviceRequestId);
    }

    public List<Vehicle> eligibleVehicles(LocalDateTime start, LocalDateTime end) {
        return vehicles.listEligible(start, end);
    }

    public List<Employee> eligibleOperators(LocalDateTime start, LocalDateTime end) {
        return employees.listEligible(start, end);
    }

    /** FR-TRP-2 / §8.1: validate everything, insert the trip and mark the request assigned. */
    public Result<Trip> assign(long requestId, long vehicleId, long operatorId) {
        if (!Session.has(Permissions.TRIPS_ASSIGN)) {
            return Result.err("No tiene permiso para asignar viajes");
        }
        Result<Long> saved = trips.assign(requestId, vehicleId, operatorId, Session.userId());
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return trips.findById(saved.value()).map(Result::ok).orElse(Result.err("Viaje no encontrado"));
    }

    /** BR-15: reassign before departure, re-validated like a new assignment and audited. */
    public Result<Trip> reassign(long tripId, long vehicleId, long operatorId) {
        if (!Session.has(Permissions.TRIPS_ASSIGN)) {
            return Result.err("No tiene permiso para reasignar viajes");
        }
        Result<Void> moved = trips.reassign(tripId, vehicleId, operatorId, Session.userId());
        return afterMove(moved, tripId);
    }

    /** Records departure: the trip and request move to in_transit, resources to on_trip. */
    public Result<Trip> depart(long tripId) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para registrar la salida");
        }
        return afterMove(trips.depart(tripId, Session.userId()), tripId);
    }

    /** Records arrival: trip completed, resources freed, mileage advanced (BR-21). */
    public Result<Trip> arrive(long tripId, BigDecimal actualKm) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para registrar la llegada");
        }
        return afterMove(trips.arrive(tripId, actualKm, Session.userId()), tripId);
    }

    /**
     * Time-driven reconciliation run on load/reload: confirms dates (authorized -> scheduled)
     * and departs assigned trips whose planned start has already passed.
     */
    public int sweepLifecycle() {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return 0;
        }
        return trips.sweepLifecycle(Session.userId());
    }

    /** Cancels a scheduled trip and its request, freeing the resources. */
    public Result<Trip> cancel(long tripId, String reason) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para cancelar viajes");
        }
        return afterMove(trips.cancel(tripId, reason, Session.userId()), tripId);
    }

    /**
     * BR-14: careful cascade. Removes the trip with its costs, advances, incidents and
     * delivery, unlinks fuel loads, and sends the request back to scheduled.
     */
    public Result<Void> delete(long tripId) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para eliminar viajes");
        }
        return trips.delete(tripId, Session.userId());
    }

    /** FR-DEL-2 / BR-13: closes a delivered request only when its delivery is complete. */
    public Result<Void> closeRequest(long requestId) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para cerrar solicitudes");
        }
        return requests.close(requestId, Session.userId());
    }

    /** BR-26: the trip's planned stops with their recorded arrivals. */
    public List<TripStop> stops(long tripId) {
        return tripStops.listByTrip(tripId);
    }

    /** BR-26: records (or corrects) the actual arrival at one of the trip's stops. */
    public Result<Void> saveStopArrival(long tripId, long routeStopId, LocalDateTime arrivedAt,
            String notes) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para registrar llegadas a paradas");
        }
        return tripStops.saveArrival(tripId, routeStopId, arrivedAt, notes, Session.userId());
    }

    public Result<Void> deleteStopArrival(long arrivalId) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para quitar llegadas a paradas");
        }
        tripStops.deleteArrival(arrivalId);
        return Result.ok(null);
    }

    private Result<Trip> afterMove(Result<Void> moved, long tripId) {
        if (moved.isErr()) {
            return Result.err(moved.problems());
        }
        return trips.findById(tripId).map(Result::ok).orElse(Result.err("Viaje no encontrado"));
    }
}
