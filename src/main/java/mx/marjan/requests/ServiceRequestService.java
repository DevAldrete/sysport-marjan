package mx.marjan.requests;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class ServiceRequestService {

    private final ServiceRequestRepository requests = new ServiceRequestRepository();

    public List<ServiceRequest> search(RequestFilter filter) {
        return requests.search(filter);
    }

    public Optional<ServiceRequest> find(long id) {
        return requests.findById(id);
    }

    public List<ServiceRequest> listByStatus(RequestStatus status) {
        return requests.listByStatus(status);
    }

    /** FR-INV-1: requests whose authorized rate has no invoice yet. */
    public List<ServiceRequest> pendingBilling() {
        return requests.listPendingBilling();
    }

    /** BR-01: the database assigns the next folio for the request's year. */
    public Result<ServiceRequest> create(ServiceRequest draft) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para crear solicitudes");
        }
        Result<Long> saved = requests.create(draft, Session.userId());
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return requests.findById(saved.value())
                .map(Result::ok)
                .orElse(Result.err("Solicitud no encontrada"));
    }

    /** BR-14: careful cascade; removes the request with its trip, costs, delivery and invoices. */
    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar solicitudes");
        }
        return requests.delete(id);
    }

    public Result<ServiceRequest> update(ServiceRequest request) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para modificar solicitudes");
        }
        Result<Void> saved = requests.update(request, Session.userId());
        return saved.isErr() ? Result.err(saved.problems()) : Result.ok(request);
    }

    public Result<ServiceRequest> authorize(long id, BigDecimal rate) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para autorizar solicitudes");
        }
        return afterMove(requests.authorize(id, rate, Session.userId()), id);
    }

    public Result<ServiceRequest> schedule(long id, LocalDateTime pickup, LocalDateTime delivery) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para programar solicitudes");
        }
        return afterMove(requests.schedule(id, pickup, delivery, Session.userId()), id);
    }

    public Result<ServiceRequest> cancel(long id, String reason) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para cancelar solicitudes");
        }
        return afterMove(requests.cancel(id, reason, Session.userId()), id);
    }

    private Result<ServiceRequest> afterMove(Result<Void> moved, long id) {
        if (moved.isErr()) {
            return Result.err(moved.problems());
        }
        return requests.findById(id).map(Result::ok).orElse(Result.err("Solicitud no encontrada"));
    }
}
