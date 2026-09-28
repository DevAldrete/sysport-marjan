package mx.marjan.operators;

import java.util.List;
import java.util.Optional;
import mx.marjan.security.Caller;
import mx.marjan.security.Permissions;
import mx.marjan.shared.Result;

public class EmployeeService {

    private final EmployeeRepository employees = new EmployeeRepository();
    private final Caller caller;

    public EmployeeService(Caller caller) {
        this.caller = caller;
    }

    public List<Employee> search(String term) {
        return employees.search(term);
    }

    public Optional<Employee> find(long id) {
        return employees.findById(id);
    }

    public Result<Employee> save(Employee employee) {
        if (!caller.has(Permissions.OPERATORS_WRITE)) {
            return Result.err("No tiene permiso para modificar operadores");
        }
        Result<Long> saved = employees.save(employee);
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return Result.ok(new Employee(saved.value(), employee.name(), employee.address(),
                employee.phone(), employee.email(), employee.rfc(), employee.curp(),
                employee.emergencyContactName(), employee.emergencyContactPhone(),
                employee.license(), employee.status()));
    }

    public Result<Void> delete(long id) {
        if (!caller.has(Permissions.OPERATORS_WRITE)) {
            return Result.err("No tiene permiso para eliminar operadores");
        }
        return employees.delete(id);
    }

    public Result<Void> setStatus(long id, EmployeeStatus status) {
        if (!caller.has(Permissions.OPERATORS_WRITE)) {
            return Result.err("No tiene permiso para modificar operadores");
        }
        return employees.setStatus(id, status);
    }
}
