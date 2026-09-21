package mx.marjan.trips;

import java.util.List;
import java.util.Optional;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequestRepository;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

public class DeliveryService {

    private final DeliveryRepository deliveries = new DeliveryRepository();
    private final TripRepository trips = new TripRepository();
    private final ServiceRequestRepository requests = new ServiceRequestRepository();

    public Optional<Delivery> findByTrip(long tripId) {
        return deliveries.findByTrip(tripId);
    }

    public List<Delivery> listAll() {
        return deliveries.listAll();
    }

    /** FR-DEL-1: registers the delivery and advances the request to delivered. */
    public Result<Void> register(Delivery delivery) {
        if (!Session.has(Permissions.DELIVERIES_WRITE)) {
            return Result.err("No tiene permiso para registrar entregas");
        }
        if (delivery.tripId() == 0) {
            return Result.err("Debe seleccionar un viaje");
        }
        if (delivery.actualDatetime() == null) {
            return Result.err("La fecha y hora de entrega son obligatorias");
        }
        if (delivery.receivedBy() == null || delivery.receivedBy().isBlank()) {
            return Result.err("Debe indicar quien recibio la mercancia");
        }
        if (delivery.evidenceReference() == null || delivery.evidenceReference().isBlank()) {
            return Result.err("La referencia de evidencia es obligatoria");
        }
        long userId = Session.userId();
        Delivery complete = new Delivery(delivery.id(), delivery.tripId(), delivery.tripFolio(),
                delivery.actualDatetime(), delivery.receivedBy(), delivery.evidenceReference(),
                DeliveryStatus.COMPLETE);
        return Database.inTransaction(connection -> {
            deliveries.save(connection, complete, userId);
            Optional<Trip> tripOpt = trips.findById(connection, delivery.tripId());
            if (tripOpt.isPresent()) {
                Optional<mx.marjan.requests.ServiceRequest> requestOpt =
                        requests.findById(connection, tripOpt.get().serviceRequestId());
                if (requestOpt.isPresent()) {
                    mx.marjan.requests.ServiceRequest request = requestOpt.get();
                    if (request.status() == RequestStatus.IN_TRANSIT
                            || request.status() == RequestStatus.ASSIGNED) {
                        requests.update(connection, request.withStatus(RequestStatus.DELIVERED), userId);
                    }
                }
            }
            return Result.<Void>ok(null);
        });
    }
}
