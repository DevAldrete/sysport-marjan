package mx.marjan.trips;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mx.marjan.fleet.Vehicle;
import mx.marjan.fleet.VehicleRepository;
import mx.marjan.fleet.VehicleStatus;
import mx.marjan.operators.Employee;
import mx.marjan.operators.EmployeeRepository;
import mx.marjan.operators.EmployeeStatus;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.requests.ServiceRequestRepository;
import mx.marjan.security.AuditRepository;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Result;

/**
 * Trip assignment and execution. Assignment runs in one transaction with row
 * locks so two dispatchers cannot book the same vehicle or operator.
 */
public class TripService {

    private final TripRepository trips = new TripRepository();
    private final VehicleRepository vehicles = new VehicleRepository();
    private final EmployeeRepository employees = new EmployeeRepository();
    private final ServiceRequestRepository requests = new ServiceRequestRepository();
    private final DeliveryRepository deliveries = new DeliveryRepository();
    private final AuditRepository audit = new AuditRepository();

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

    /** FR-TRP-2 / §8.1: validate everything, then insert the trip and mark the request assigned. */
    public Result<Trip> assign(long requestId, long vehicleId, long operatorId) {
        if (!Session.has(Permissions.TRIPS_ASSIGN)) {
            return Result.err("No tiene permiso para asignar viajes");
        }
        long userId = Session.userId();
        return Database.inTransaction(connection -> {
            trips.lockVehicle(connection, vehicleId);
            trips.lockEmployee(connection, operatorId);

            Optional<ServiceRequest> requestOpt = requests.findById(connection, requestId);
            if (requestOpt.isEmpty()) {
                connection.rollback();
                return Result.<Trip>err("Solicitud no encontrada");
            }
            ServiceRequest request = requestOpt.get();
            Vehicle vehicle = vehicles.findById(connection, vehicleId).orElse(null);
            Employee employee = employees.findById(connection, operatorId).orElse(null);

            List<Trip> vehicleTrips = List.of();
            List<Trip> employeeTrips = List.of();
            if (request.pickupScheduled() != null && request.deliveryScheduled() != null) {
                vehicleTrips = trips.overlappingForVehicle(connection, vehicleId,
                        request.pickupScheduled(), request.deliveryScheduled(), 0);
                employeeTrips = trips.overlappingForEmployee(connection, operatorId,
                        request.pickupScheduled(), request.deliveryScheduled(), 0);
            }
            Result<AssignmentPlan> validation = AssignmentRules.validate(
                    request, vehicle, vehicleTrips, employee, employeeTrips, Dates.today());
            if (validation.isErr()) {
                connection.rollback();
                return Result.<Trip>err(validation.problems());
            }
            AssignmentPlan plan = validation.value();
            long tripId = trips.insert(connection, plan, null, userId);
            requests.update(connection, request.withStatus(RequestStatus.ASSIGNED), userId);
            audit.log(connection, "trip", tripId, "assigned",
                    "vehicle=" + vehicleId + ", operator=" + operatorId);
            return Result.ok(trips.findById(connection, tripId).orElseThrow());
        });
    }

    /** BR-15: reassign before departure, re-validated like a new assignment and audited. */
    public Result<Trip> reassign(long tripId, long vehicleId, long operatorId) {
        if (!Session.has(Permissions.TRIPS_ASSIGN)) {
            return Result.err("No tiene permiso para reasignar viajes");
        }
        long userId = Session.userId();
        return Database.inTransaction(connection -> {
            Optional<Trip> tripOpt = trips.findById(connection, tripId);
            if (tripOpt.isEmpty()) {
                connection.rollback();
                return Result.<Trip>err("Viaje no encontrado");
            }
            Trip trip = tripOpt.get();
            if (trip.status() != TripStatus.SCHEDULED) {
                connection.rollback();
                return Result.<Trip>err("Solo se puede reasignar un viaje programado (aun no inicia)");
            }
            Optional<ServiceRequest> requestOpt = requests.findById(connection, trip.serviceRequestId());
            if (requestOpt.isEmpty()) {
                connection.rollback();
                return Result.<Trip>err("Solicitud no encontrada");
            }
            ServiceRequest request = requestOpt.get();
            trips.lockVehicle(connection, vehicleId);
            trips.lockEmployee(connection, operatorId);

            Vehicle vehicle = vehicles.findById(connection, vehicleId).orElse(null);
            Employee employee = employees.findById(connection, operatorId).orElse(null);
            List<Trip> vehicleTrips = trips.overlappingForVehicle(connection, vehicleId,
                    request.pickupScheduled(), request.deliveryScheduled(), tripId);
            List<Trip> employeeTrips = trips.overlappingForEmployee(connection, operatorId,
                    request.pickupScheduled(), request.deliveryScheduled(), tripId);

            Result<AssignmentPlan> validation = AssignmentRules.validate(
                    request, vehicle, vehicleTrips, employee, employeeTrips, Dates.today());
            if (validation.isErr()) {
                connection.rollback();
                return Result.<Trip>err(validation.problems());
            }
            trips.reassign(connection, tripId, vehicleId, operatorId, userId);
            audit.log(connection, "trip", tripId, "reassigned",
                    "from vehicle=" + trip.vehicleId() + "/operator=" + trip.employeeId()
                            + " to vehicle=" + vehicleId + "/operator=" + operatorId);
            return Result.ok(trips.findById(connection, tripId).orElseThrow());
        });
    }

    /** Records departure: the trip and request move to in_transit, resources to on_trip. */
    public Result<Trip> depart(long tripId) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para registrar la salida");
        }
        long userId = Session.userId();
        return Database.inTransaction(connection -> {
            Optional<Trip> tripOpt = trips.findById(connection, tripId);
            if (tripOpt.isEmpty()) {
                connection.rollback();
                return Result.<Trip>err("Viaje no encontrado");
            }
            Trip trip = tripOpt.get();
            if (trip.status() != TripStatus.SCHEDULED) {
                connection.rollback();
                return Result.<Trip>err("El viaje no esta programado");
            }
            LocalDateTime departure = Dates.now();
            trips.depart(connection, tripId, departure, userId);
            vehicles.updateStatus(connection, trip.vehicleId(), VehicleStatus.ON_TRIP);
            employees.updateStatus(connection, trip.employeeId(), EmployeeStatus.ON_TRIP);
            requests.findById(connection, trip.serviceRequestId()).ifPresent(request -> {
                try {
                    requests.update(connection, request.withStatus(RequestStatus.IN_TRANSIT), userId);
                } catch (java.sql.SQLException failure) {
                    throw new RuntimeException(failure);
                }
            });
            return Result.ok(trips.findById(connection, tripId).orElseThrow());
        });
    }

    /** Records arrival: trip completed, resources freed, mileage advanced (BR-21). */
    public Result<Trip> arrive(long tripId, BigDecimal actualKm) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para registrar la llegada");
        }
        long userId = Session.userId();
        return Database.inTransaction(connection -> {
            Optional<Trip> tripOpt = trips.findById(connection, tripId);
            if (tripOpt.isEmpty()) {
                connection.rollback();
                return Result.<Trip>err("Viaje no encontrado");
            }
            Trip trip = tripOpt.get();
            if (trip.status() != TripStatus.IN_TRANSIT) {
                connection.rollback();
                return Result.<Trip>err("El viaje no esta en transito");
            }
            trips.arrive(connection, tripId, Dates.now(), actualKm, userId);
            vehicles.updateStatus(connection, trip.vehicleId(), VehicleStatus.AVAILABLE);
            vehicles.updateMileageIfHigher(connection, trip.vehicleId(), actualKm);
            employees.updateStatus(connection, trip.employeeId(), EmployeeStatus.AVAILABLE);
            return Result.ok(trips.findById(connection, tripId).orElseThrow());
        });
    }

    /** Cancels a scheduled trip and its request, freeing the resources. */
    public Result<Trip> cancel(long tripId, String reason) {
        if (!Session.has(Permissions.TRIPS_WRITE)) {
            return Result.err("No tiene permiso para cancelar viajes");
        }
        if (reason == null || reason.isBlank()) {
            return Result.err("Debe indicar el motivo de la cancelacion");
        }
        long userId = Session.userId();
        return Database.inTransaction(connection -> {
            Optional<Trip> tripOpt = trips.findById(connection, tripId);
            if (tripOpt.isEmpty()) {
                connection.rollback();
                return Result.<Trip>err("Viaje no encontrado");
            }
            Trip trip = tripOpt.get();
            if (trip.status() != TripStatus.SCHEDULED) {
                connection.rollback();
                return Result.<Trip>err("Solo se puede cancelar un viaje programado");
            }
            trips.updateStatus(connection, tripId, TripStatus.CANCELLED, userId);
            requests.findById(connection, trip.serviceRequestId()).ifPresent(request -> {
                try {
                    requests.update(connection,
                            request.withNotes(reason).withStatus(RequestStatus.CANCELLED), userId);
                } catch (java.sql.SQLException failure) {
                    throw new RuntimeException(failure);
                }
            });
            audit.log(connection, "trip", tripId, "cancelled", reason);
            return Result.ok(trips.findById(connection, tripId).orElseThrow());
        });
    }

    /** FR-DEL-2 / BR-13: closes a delivered request only when its delivery is complete. */
    public Result<Void> closeRequest(long requestId) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para cerrar solicitudes");
        }
        long userId = Session.userId();
        return Database.inTransaction(connection -> {
            Optional<ServiceRequest> requestOpt = requests.findById(connection, requestId);
            if (requestOpt.isEmpty()) {
                connection.rollback();
                return Result.<Void>err("Solicitud no encontrada");
            }
            ServiceRequest request = requestOpt.get();
            if (!request.status().canMoveTo(RequestStatus.CLOSED)) {
                connection.rollback();
                return Result.<Void>err("Solo se puede cerrar una solicitud entregada");
            }
            Optional<Trip> trip = trips.findByServiceRequest(connection, requestId);
            Optional<Delivery> delivery = trip.isPresent()
                    ? deliveries.findByTrip(connection, trip.get().id())
                    : Optional.empty();
            Result<Void> closing = ClosingRules.canClose(request, delivery);
            if (closing.isErr()) {
                connection.rollback();
                return Result.<Void>err(closing.problems());
            }
            requests.update(connection, request.withStatus(RequestStatus.CLOSED), userId);
            return Result.<Void>ok(null);
        });
    }
}
