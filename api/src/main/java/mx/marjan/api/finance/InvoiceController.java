package mx.marjan.api.finance;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.finance.Invoice;
import mx.marjan.finance.InvoiceService;
import mx.marjan.finance.InvoiceStatus;
import mx.marjan.finance.Payment;
import mx.marjan.finance.PaymentMethod;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.requests.ServiceRequestService;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.shared.Dates;

/** Invoices and payments (FR-INV-1..4). */
@Controller("/api/invoices")
public class InvoiceController {

    @Get
    @Secured(Permissions.INVOICES_READ)
    public List<Invoice> search(Authentication authentication, @Nullable @QueryValue String status,
            @Nullable @QueryValue Long clientId) {
        InvoiceStatus invoiceStatus = status == null || status.isBlank() ? null : InvoiceStatus.fromDb(status);
        return service(authentication).search(invoiceStatus, clientId);
    }

    @Get("/{id}")
    @Secured(Permissions.INVOICES_READ)
    public HttpResponse<?> find(Authentication authentication, long id) {
        return service(authentication).find(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Get("/{id}/payments")
    @Secured(Permissions.PAYMENTS_READ)
    public List<Payment> payments(Authentication authentication, long id) {
        return service(authentication).paymentsFor(id);
    }

    /** BR-20: create an invoice from a delivered/closed request; amount defaults to the agreed rate. */
    @Post
    @Secured(Permissions.INVOICES_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body InvoiceWrite body) {
        Caller caller = Callers.forAuthentication(authentication);
        ServiceRequest request = new ServiceRequestService(caller).find(body.requestId()).orElse(null);
        if (request == null) {
            return HttpResponse.unprocessableEntity()
                    .body(Map.of("problems", List.of("Solicitud no encontrada")));
        }
        BigDecimal amount = body.amount() != null ? body.amount() : request.agreedRate();
        if (amount == null) {
            return HttpResponse.unprocessableEntity()
                    .body(Map.of("problems", List.of("Indique el importe de la factura")));
        }
        LocalDate issueDate = body.issueDate() != null ? body.issueDate() : Dates.today();
        return Responses.of(new InvoiceService(caller).createFromRequest(request, issueDate, amount));
    }

    /** BR-19: partial payments allowed, overpayment rejected, status re-derived. */
    @Post("/{id}/payments")
    @Secured(Permissions.PAYMENTS_WRITE)
    public HttpResponse<?> registerPayment(Authentication authentication, long id,
            @Body PaymentWrite body) {
        PaymentMethod method = body.method() == null || body.method().isBlank()
                ? PaymentMethod.CASH : PaymentMethod.fromDb(body.method());
        LocalDate date = body.date() != null ? body.date() : Dates.today();
        return Responses.of(service(authentication).registerPayment(id, body.amount(), date, method));
    }

    @Delete("/payments/{id}")
    @Secured(Permissions.PAYMENTS_WRITE)
    public HttpResponse<?> deletePayment(Authentication authentication, long id) {
        return Responses.of(service(authentication).deletePayment(id));
    }

    @Delete("/{id}")
    @Secured(Permissions.INVOICES_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    /** BR-19: cancel a pending/overdue invoice that has no payments yet. */
    @Post("/{id}/cancel")
    @Secured(Permissions.INVOICES_WRITE)
    public HttpResponse<?> cancel(Authentication authentication, long id) {
        return Responses.of(service(authentication).cancel(id));
    }

    @Post("/refresh-statuses")
    @Secured(Permissions.INVOICES_WRITE)
    public Map<String, Integer> refreshStatuses(Authentication authentication) {
        return Map.of("updated", service(authentication).refreshStatuses(Dates.today()));
    }

    private InvoiceService service(Authentication authentication) {
        return new InvoiceService(Callers.forAuthentication(authentication));
    }

    public record InvoiceWrite(long requestId, LocalDate issueDate, BigDecimal amount) {}

    public record PaymentWrite(BigDecimal amount, LocalDate date, String method) {}
}
