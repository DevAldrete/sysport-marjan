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
    private final CargoPackageService cargoPackages = new CargoPackageService();

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
        return create(draft, List.of());
    }

    /** Creates the request and then its package list (the latter atomically). */
    public Result<ServiceRequest> create(ServiceRequest draft, List<CargoPackage> packages) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para crear solicitudes");
        }
        Result<Long> saved = requests.create(draft, Session.userId());
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        Result<Void> packagesSaved = cargoPackages.replace(saved.value(), packages);
        if (packagesSaved.isErr()) {
            return Result.err(packagesSaved.problems());
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
        return update(request, null);
    }

    /**
     * Updates the request and, when {@code packages} is not null, replaces its
     * package list (null leaves the packages untouched).
     */
    public Result<ServiceRequest> update(ServiceRequest request, List<CargoPackage> packages) {
        if (!Session.has(Permissions.REQUESTS_WRITE)) {
            return Result.err("No tiene permiso para modificar solicitudes");
        }
        Result<Void> saved = requests.update(request, Session.userId());
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        if (packages != null) {
            Result<Void> packagesSaved = cargoPackages.replace(request.id(), packages);
            if (packagesSaved.isErr()) {
                return Result.err(packagesSaved.problems());
            }
        }
        return requests.findById(request.id()).map(Result::ok).orElse(Result.ok(request));
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
