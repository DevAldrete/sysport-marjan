package mx.marjan.api.finance;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.finance.Advance;
import mx.marjan.finance.AdvanceBalance;
import mx.marjan.finance.AdvanceService;
import mx.marjan.security.Permissions;

/** Driver advances and their settlement (FR-ADV-1..3). */
@Controller("/api/advances")
public class AdvanceController {

    @Get
    @Secured(Permissions.ADVANCES_READ)
    public List<Advance> list(Authentication authentication, @QueryValue long tripId) {
        return service(authentication).listByTrip(tripId);
    }

    @Get("/balance")
    @Secured(Permissions.ADVANCES_READ)
    public BalanceView balance(Authentication authentication, @QueryValue long tripId) {
        AdvanceBalance balance = service(authentication).balanceForTrip(tripId);
        return switch (balance.outcome()) {
            case AdvanceBalance.Settled ignored ->
                new BalanceView(balance.given(), balance.proven(), "settled", null, balance.label());
            case AdvanceBalance.OperatorOwes owes ->
                new BalanceView(balance.given(), balance.proven(), "operator_owes", owes.amount(), balance.label());
            case AdvanceBalance.CompanyOwes owes ->
                new BalanceView(balance.given(), balance.proven(), "company_owes", owes.amount(), balance.label());
        };
    }

    @Post
    @Secured(Permissions.ADVANCES_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body Advance advance) {
        Advance record = new Advance(0, advance.tripId(), null, advance.employeeId(), null,
                advance.amountGiven(), advance.deliveredDate(), null, null);
        return Responses.of(service(authentication).register(record));
    }

    @Post("/{id}/settle")
    @Secured(Permissions.ADVANCES_WRITE)
    public HttpResponse<?> settle(Authentication authentication, long id) {
        return Responses.of(service(authentication).settle(id));
    }

    @Delete("/{id}")
    @Secured(Permissions.ADVANCES_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private AdvanceService service(Authentication authentication) {
        return new AdvanceService(Callers.forAuthentication(authentication));
    }

    /** Flattened BR-16 outcome so the client does not have to decode a sealed type. */
    public record BalanceView(java.math.BigDecimal given, java.math.BigDecimal proven, String outcome,
            java.math.BigDecimal amount, String label) {}
}
