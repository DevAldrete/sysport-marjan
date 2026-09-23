package mx.marjan.operators;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

public class EmployeeService {

    private final EmployeeRepository employees = new EmployeeRepository();

    public List<Employee> search(String term) {
        return employees.search(term);
    }

    public Optional<Employee> find(long id) {
        return employees.findById(id);
    }

    public Result<Employee> save(Employee employee) {
        if (!Session.has(Permissions.OPERATORS_WRITE)) {
            return Result.err("No tiene permiso para modificar operadores");
        }
        List<String> problems = new ArrayList<>();
        if (employee.name() == null || employee.name().isBlank()) {
            problems.add("El nombre es obligatorio");
        }
        if (employee.phone() == null || employee.phone().isBlank()) {
            problems.add("El telefono es obligatorio");
        } else if (!mx.marjan.shared.Validators.isValidPhone(employee.phone())) {
            problems.add("El telefono no tiene un formato valido");
        }
        if (!mx.marjan.shared.Validators.isValidEmail(employee.email())) {
            problems.add("El correo electronico no tiene un formato valido");
        }
        if (!mx.marjan.shared.Validators.isValidRfc(employee.rfc())) {
            problems.add("El RFC no tiene un formato valido");
        }
        if (!mx.marjan.shared.Validators.isValidCurp(employee.curp())) {
            problems.add("La CURP no tiene un formato valido");
        }
        if (!mx.marjan.shared.Validators.isValidPhone(employee.emergencyContactPhone())) {
            problems.add("El telefono de emergencia no tiene un formato valido");
        }
        License license = employee.license();
        if (license != null && (license.licenseNumber() == null || license.licenseNumber().isBlank())) {
            problems.add("El numero de licencia es obligatorio");
        } else if (license != null && !mx.marjan.shared.Validators.isValidLicenseNumber(license.licenseNumber())) {
            problems.add("El numero de licencia no tiene un formato valido");
        }
        if (license != null && license.expirationDate() == null) {
            problems.add("La fecha de vencimiento de la licencia es obligatoria");
        }
        if (license != null && (!mx.marjan.shared.Validators.isValidDate(license.issueDate())
                || !mx.marjan.shared.Validators.isValidDate(license.expirationDate()))) {
            problems.add("Las fechas de la licencia no son validas");
        } else if (license != null && license.issueDate() != null
                && license.issueDate().isAfter(license.expirationDate())) {
            problems.add("La fecha de expedicion no puede ser posterior al vencimiento");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        employees.save(employee);
        return Result.ok(employee);
    }

    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.OPERATORS_WRITE)) {
            return Result.err("No tiene permiso para eliminar operadores");
        }
        employees.delete(id);
        return Result.ok(null);
    }

    public Result<Void> setStatus(long id, EmployeeStatus status) {
        if (!Session.has(Permissions.OPERATORS_WRITE)) {
            return Result.err("No tiene permiso para modificar operadores");
        }
        employees.updateStatus(id, status);
        return Result.ok(null);
    }
}
