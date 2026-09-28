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
import mx.marjan.finance.Expense;
import mx.marjan.finance.ExpenseService;
import mx.marjan.finance.ExpenseType;
import mx.marjan.security.Permissions;

/** Trip expenses (FR-EXP-1). */
@Controller("/api/expenses")
public class ExpenseController {

    @Get
    @Secured(Permissions.EXPENSES_READ)
    public List<Expense> list(Authentication authentication, @QueryValue long tripId) {
        return service(authentication).listByTrip(tripId);
    }

    @Post
    @Secured(Permissions.EXPENSES_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body Expense expense) {
        Expense record = new Expense(0, expense.tripId(), null,
                expense.type() == null ? ExpenseType.OTHER : expense.type(), expense.amount(),
                expense.expenseDate(), expense.description());
        return Responses.of(service(authentication).register(record));
    }

    @Delete("/{id}")
    @Secured(Permissions.EXPENSES_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private ExpenseService service(Authentication authentication) {
        return new ExpenseService(Callers.forAuthentication(authentication));
    }
}
