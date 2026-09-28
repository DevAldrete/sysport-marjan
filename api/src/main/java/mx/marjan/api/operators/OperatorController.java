package mx.marjan.api.operators;

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
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.operators.Employee;
import mx.marjan.operators.EmployeeService;
import mx.marjan.operators.EmployeeStatus;
import mx.marjan.operators.License;
import mx.marjan.security.Permissions;

/** Operator CRUD, licences and manual status (FR-OPR-1/2). */
@Controller("/api/operators")
public class OperatorController {

    @Get
    @Secured(Permissions.OPERATORS_READ)
    public List<Employee> search(Authentication authentication, @Nullable @QueryValue String term) {
        return service(authentication).search(term);
    }

    @Get("/{id}")
    @Secured(Permissions.OPERATORS_READ)
    public HttpResponse<?> find(Authentication authentication, long id) {
        return service(authentication).find(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post
    @Secured(Permissions.OPERATORS_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body Employee employee) {
        return Responses.of(service(authentication).save(normalize(employee, 0)));
    }

    @Put("/{id}")
    @Secured(Permissions.OPERATORS_WRITE)
    public HttpResponse<?> update(Authentication authentication, long id, @Body Employee employee) {
        return Responses.of(service(authentication).save(normalize(employee, id)));
    }

    @Post("/{id}/status")
    @Secured(Permissions.OPERATORS_WRITE)
    public HttpResponse<?> changeStatus(Authentication authentication, long id,
            @Body StatusUpdate body) {
        return Responses.of(service(authentication).setStatus(id,
                body.status() == null ? EmployeeStatus.AVAILABLE : body.status()));
    }

    @Delete("/{id}")
    @Secured(Permissions.OPERATORS_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private EmployeeService service(Authentication authentication) {
        return new EmployeeService(Callers.forAuthentication(authentication));
    }

    private Employee normalize(Employee employee, long id) {
        License license = employee.license();
        if (license != null && (license.licenseNumber() == null || license.licenseNumber().isBlank())) {
            license = null;
        }
        return new Employee(id, employee.name(), employee.address(), employee.phone(), employee.email(),
                employee.rfc(), employee.curp(), employee.emergencyContactName(),
                employee.emergencyContactPhone(), license,
                employee.status() == null ? EmployeeStatus.AVAILABLE : employee.status());
    }

    public record StatusUpdate(EmployeeStatus status) {}
}
