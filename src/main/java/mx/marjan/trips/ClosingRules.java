package mx.marjan.trips;

import java.util.Optional;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.shared.Result;

/** BR-13: a request that requires documents can only close with a complete delivery. */
public final class ClosingRules {

    private ClosingRules() {}

    public static Result<Void> canClose(ServiceRequest request, Optional<Delivery> delivery) {
        if (!request.requiresDocuments()) {
            return Result.ok(null);
        }
        if (delivery.isEmpty()) {
            return Result.err("La solicitud requiere documentacion y no tiene entrega registrada");
        }
        Delivery record = delivery.get();
        if (record.status() != DeliveryStatus.COMPLETE
                || record.receivedBy() == null || record.receivedBy().isBlank()
                || record.evidenceReference() == null || record.evidenceReference().isBlank()) {
            return Result.err("La entrega no esta completa (recibido por y evidencia son obligatorios)");
        }
        return Result.ok(null);
    }
}
