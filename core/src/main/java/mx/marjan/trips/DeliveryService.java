package mx.marjan.trips;

import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class DeliveryService {

    private final DeliveryRepository deliveries = new DeliveryRepository();

    public Optional<Delivery> findByTrip(long tripId) {
        return deliveries.findByTrip(tripId);
    }

    /** FR-DEL-1: registers the delivery and advances the request to delivered. */
    public Result<Void> register(Delivery delivery) {
        if (!Session.has(Permissions.DELIVERIES_WRITE)) {
            return Result.err("No tiene permiso para registrar entregas");
        }
        Result<Long> saved = deliveries.save(delivery, Session.userId());
        return saved.isOk() ? Result.ok(null) : Result.err(saved.problems());
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.DELIVERIES_WRITE)) {
            return Result.err("No tiene permiso para eliminar entregas");
        }
        deliveries.delete(id);
        return Result.ok(null);
    }
}
