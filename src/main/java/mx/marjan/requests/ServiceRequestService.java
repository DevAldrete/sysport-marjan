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

    /** FR-INV-1: requests whose authorized rate has no invoice yet. */
    public List<ServiceRequest> pendingBilling() {
        return requests.listPendingBilling();
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
        if (!mx.marjan.shared.Validators.isMeasure(draft.estimatedWeight())) {
            problems.add("El peso estimado es invalido o excede el maximo permitido");
        }
        if (!mx.marjan.shared.Validators.isValidDate(
                draft.pickupScheduled() == null ? null : draft.pickupScheduled().toLocalDate())
                || !mx.marjan.shared.Validators.isValidDate(
                        draft.deliveryScheduled() == null ? null : draft.deliveryScheduled().toLocalDate())) {
            problems.add("Las fechas programadas no son validas");
        }
        if ((draft.pickupScheduled() == null) != (draft.deliveryScheduled() == null)) {
            problems.add("Debe indicar ambas fechas o ninguna");
        } else if (draft.pickupScheduled() != null
                && !draft.deliveryScheduled().isAfter(draft.pickupScheduled())) {
            problems.add("La fecha de entrega debe ser posterior a la de recoleccion");
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
        long userId = Session.userId();
        Long id = mx.marjan.shared.Database.inTransaction(connection ->
                requests.insert(connection, toInsert, userId));
        return Result.ok(withId(toInsert, id));
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar solicitudes");
        }
        requests.delete(id);
        return Result.ok(null);
    }

    public Result<ServiceRequest> update(ServiceRequest request) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para modificar solicitudes");
        }
        if (!mx.marjan.shared.Validators.isMeasure(request.estimatedWeight())) {
            return Result.err("El peso estimado es invalido o excede el maximo permitido");
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
