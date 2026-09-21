package mx.marjan.requests;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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

    /** BR-01: assigns the next folio for the request's year. */
    public Result<ServiceRequest> create(ServiceRequest draft) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para crear solicitudes");
        }
        List<String> problems = new ArrayList<>();
        if (draft.clientId() == 0) {
            problems.add("Debe seleccionar un cliente");
        }
        if (draft.routeId() == 0) {
            problems.add("Debe seleccionar una ruta");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        int year = draft.pickupScheduled() != null ? draft.pickupScheduled().getYear() : LocalDate.now().getYear();
        String folio = FolioGenerator.format(year, requests.nextSequence(year));
        ServiceRequest toInsert = new ServiceRequest(0, folio, draft.clientId(), draft.clientName(),
                draft.routeId(), draft.routeLabel(), draft.cargoDescription(), draft.estimatedWeight(),
                draft.pickupScheduled(), draft.deliveryScheduled(), draft.agreedRate(),
                draft.requiresDocuments(), RequestStatus.REQUESTED, draft.notes(), LocalDateTime.now());
        long id = requests.insert(toInsert, Session.userId());
        return Result.ok(withId(toInsert, id));
    }

    public Result<ServiceRequest> update(ServiceRequest request) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para modificar solicitudes");
        }
        requests.update(request, Session.userId());
        return Result.ok(request);
    }

    public Result<ServiceRequest> authorize(long id, BigDecimal rate) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para autorizar solicitudes");
        }
        return requests.findById(id)
                .map(request -> save(ServiceRequestFlow.authorize(request, rate)))
                .orElse(Result.err("Solicitud no encontrada"));
    }

    public Result<ServiceRequest> schedule(long id, LocalDateTime pickup, LocalDateTime delivery) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para programar solicitudes");
        }
        return requests.findById(id)
                .map(request -> save(ServiceRequestFlow.schedule(request, pickup, delivery)))
                .orElse(Result.err("Solicitud no encontrada"));
    }

    public Result<ServiceRequest> cancel(long id, String reason) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para cancelar solicitudes");
        }
        return requests.findById(id)
                .map(request -> save(ServiceRequestFlow.cancel(request, reason)))
                .orElse(Result.err("Solicitud no encontrada"));
    }

    /** Used by trip assignment; the caller (TripService) already checked permission. */
    public void markAssigned(long id) {
        requests.findById(id).ifPresent(request -> {
            requests.update(request.withStatus(RequestStatus.ASSIGNED), Session.userId());
        });
    }

    public Result<ServiceRequest> moveTo(long id, RequestStatus status) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para modificar solicitudes");
        }
        return moveToInternal(id, status);
    }

    /** For orchestration from other services that already checked their own permission. */
    public Result<ServiceRequest> moveToInternal(long id, RequestStatus status) {
        return requests.findById(id)
                .map(request -> save(ServiceRequestFlow.moveTo(request, status)))
                .orElse(Result.err("Solicitud no encontrada"));
    }

    private <T> Result<ServiceRequest> save(Result<ServiceRequest> moved) {
        if (moved.isErr()) {
            return moved;
        }
        ServiceRequest request = moved.value();
        requests.update(request, Session.userId());
        return Result.ok(request);
    }

    private ServiceRequest withId(ServiceRequest request, long id) {
        return new ServiceRequest(id, request.folio(), request.clientId(), request.clientName(),
                request.routeId(), request.routeLabel(), request.cargoDescription(),
                request.estimatedWeight(), request.pickupScheduled(), request.deliveryScheduled(),
                request.agreedRate(), request.requiresDocuments(), request.status(),
                request.notes(), request.createdAt());
    }
}
