package mx.marjan.api.requests;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.operators.Employee;
import mx.marjan.requests.CargoPackage;
import mx.marjan.requests.CargoPackageService;
import mx.marjan.requests.PackageCondition;
import mx.marjan.requests.PackageUnit;
import mx.marjan.requests.RequestFilter;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.requests.ServiceRequestService;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.trips.TripService;
import mx.marjan.fleet.Vehicle;

/** Service requests: list, lifecycle, packages and assignment (FR-REQ-1..5, FR-TRP-1/2). */
@Controller("/api/requests")
public class RequestController {

    @Get
    @Secured(Permissions.REQUESTS_READ)
    public List<ServiceRequest> search(Authentication authentication,
            @Nullable @QueryValue String folio,
            @Nullable @QueryValue Long clientId,
            @Nullable @QueryValue String status,
            @Nullable @QueryValue LocalDate from,
            @Nullable @QueryValue LocalDate to) {
        RequestStatus requestStatus = status == null || status.isBlank()
                ? null : RequestStatus.fromDb(status);
        RequestFilter filter = new RequestFilter(folio, clientId, requestStatus, from, to);
        return requests(authentication).search(filter);
    }

    @Get("/pending-billing")
    @Secured(Permissions.REQUESTS_READ)
    public List<ServiceRequest> pendingBilling(Authentication authentication) {
        return requests(authentication).pendingBilling();
    }

    @Get("/{id}")
    @Secured(Permissions.REQUESTS_READ)
    public HttpResponse<?> find(Authentication authentication, long id) {
        return requests(authentication).find(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Get("/{id}/packages")
    @Secured(Permissions.REQUESTS_READ)
    public List<CargoPackage> packages(Authentication authentication, long id) {
        return new CargoPackageService(Callers.forAuthentication(authentication)).list(id);
    }

    /** FR-DEL-2: per-unit receipts recorded from the trip detail (deliveries.write). */
    @Post("/{id}/receipts")
    @Secured(Permissions.DELIVERIES_WRITE)
    public HttpResponse<?> saveReceipts(Authentication authentication, long id,
            @Body List<ReceiptLine> receipts) {
        List<CargoPackage> lines = new ArrayList<>();
        for (ReceiptLine line : receipts) {
            lines.add(new CargoPackage(line.id(), id, 0, null, null, null, null,
                    line.receivedQuantity(),
                    line.receiptCondition() == null ? null : PackageCondition.fromDb(line.receiptCondition())));
        }
        return Responses.of(new CargoPackageService(Callers.forAuthentication(authentication))
                .saveReceipts(lines));
    }

    @Get("/{id}/trip")
    @Secured(Permissions.REQUESTS_READ)
    public HttpResponse<?> trip(Authentication authentication, long id) {
        return new TripService(Callers.forAuthentication(authentication)).findByRequest(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post
    @Secured(Permissions.REQUESTS_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body RequestWrite body) {
        return Responses.of(requests(authentication).create(toDraft(body), toPackages(0, body.packages())));
    }

    @Put("/{id}")
    @Secured(Permissions.REQUESTS_WRITE)
    public HttpResponse<?> update(Authentication authentication, long id, @Body RequestWrite body) {
        ServiceRequest request = toDraft(body);
        ServiceRequest withId = new ServiceRequest(id, request.folio(), request.clientId(),
                request.clientName(), request.routeId(), request.routeLabel(),
                request.cargoDescription(), request.estimatedWeight(), request.packageCount(),
                request.packageWeight(), request.pickupScheduled(), request.deliveryScheduled(),
                request.agreedRate(), request.requiresDocuments(), request.status(),
                request.notes(), request.createdAt());
        List<CargoPackage> packages = body.packages() == null ? null : toPackages(id, body.packages());
        return Responses.of(requests(authentication).update(withId, packages));
    }

    @Post("/{id}/authorize")
    @Secured(Permissions.REQUESTS_WRITE)
    public HttpResponse<?> authorize(Authentication authentication, long id, @Body RateWrite body) {
        return Responses.of(requests(authentication).authorize(id, body.rate()));
    }

    @Post("/{id}/schedule")
    @Secured(Permissions.REQUESTS_WRITE)
    public HttpResponse<?> schedule(Authentication authentication, long id, @Body ScheduleWrite body) {
        return Responses.of(requests(authentication).schedule(id, body.pickup(), body.delivery()));
    }

    @Post("/{id}/cancel")
    @Secured(Permissions.REQUESTS_WRITE)
    public HttpResponse<?> cancel(Authentication authentication, long id, @Body ReasonWrite body) {
        return Responses.of(requests(authentication).cancel(id, body.reason()));
    }

    @Post("/{id}/close")
    @Secured(Permissions.REQUESTS_WRITE)
    public HttpResponse<?> close(Authentication authentication, long id) {
        return Responses.of(new TripService(Callers.forAuthentication(authentication)).closeRequest(id));
    }

    @Delete("/{id}")
    @Secured(Permissions.REQUESTS_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(requests(authentication).delete(id));
    }

    /** FR-TRP-1: eligible vehicles and operators for the request's planned window. */
    @Get("/{id}/assignment")
    @Secured(Permissions.TRIPS_ASSIGN)
    public HttpResponse<?> assignmentOptions(Authentication authentication, long id) {
        Optional<ServiceRequest> request = requests(authentication).find(id);
        if (request.isEmpty() || request.get().pickupScheduled() == null
                || request.get().deliveryScheduled() == null) {
            return HttpResponse.unprocessableEntity()
                    .body(java.util.Map.of("problems", List.of("La solicitud no tiene fechas programadas")));
        }
        TripService trips = new TripService(Callers.forAuthentication(authentication));
        LocalDateTime start = request.get().pickupScheduled();
        LocalDateTime end = request.get().deliveryScheduled();
        return HttpResponse.ok(new AssignmentOptions(
                trips.eligibleVehicles(start, end), trips.eligibleOperators(start, end)));
    }

    @Post("/{id}/assign")
    @Secured(Permissions.TRIPS_ASSIGN)
    public HttpResponse<?> assign(Authentication authentication, long id, @Body AssignWrite body) {
        return Responses.of(new TripService(Callers.forAuthentication(authentication))
                .assign(id, body.vehicleId(), body.operatorId()));
    }

    private ServiceRequestService requests(Authentication authentication) {
        Caller caller = Callers.forAuthentication(authentication);
        return new ServiceRequestService(caller);
    }

    private ServiceRequest toDraft(RequestWrite body) {
        return new ServiceRequest(0, null, body.clientId() == null ? 0 : body.clientId(), null,
                body.routeId() == null ? 0 : body.routeId(), null, body.cargoDescription(),
                body.estimatedWeight(), 0, null, body.pickupScheduled(), body.deliveryScheduled(),
                body.agreedRate(), body.requiresDocuments(), RequestStatus.REQUESTED, body.notes(), null);
    }

    private List<CargoPackage> toPackages(long requestId, List<PackageLine> lines) {
        List<CargoPackage> packages = new ArrayList<>();
        if (lines == null) {
            return packages;
        }
        for (PackageLine line : lines) {
            packages.add(new CargoPackage(line.id(), requestId, 0, line.description(), line.quantity(),
                    line.unit() == null ? null : PackageUnit.fromDb(line.unit()), line.unitWeight(),
                    null, null));
        }
        return packages;
    }

    public record PackageLine(long id, String description, BigDecimal quantity, String unit,
            BigDecimal unitWeight) {}

    public record ReceiptLine(long id, BigDecimal receivedQuantity, String receiptCondition) {}

    public record RequestWrite(Long clientId, Long routeId, String cargoDescription,
            BigDecimal estimatedWeight, LocalDateTime pickupScheduled, LocalDateTime deliveryScheduled,
            BigDecimal agreedRate, boolean requiresDocuments, String notes, List<PackageLine> packages) {}

    public record ScheduleWrite(LocalDateTime pickup, LocalDateTime delivery) {}

    public record RateWrite(BigDecimal rate) {}

    public record ReasonWrite(String reason) {}

    public record AssignWrite(long vehicleId, long operatorId) {}

    public record AssignmentOptions(List<Vehicle> vehicles, List<Employee> operators) {}
}
