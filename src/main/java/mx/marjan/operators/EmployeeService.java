package mx.marjan.operators;

import java.time.LocalDate;
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

    public List<Employee> listAssignable() {
        return employees.listAssignable();
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
        }
        License license = employee.license();
        if (license != null && (license.licenseNumber() == null || license.licenseNumber().isBlank())) {
            problems.add("El numero de licencia es obligatorio");
        }
        if (license != null && license.expirationDate() == null) {
            problems.add("La fecha de vencimiento de la licencia es obligatoria");
        }
        if (license != null && license.issueDate() != null
                && license.issueDate().isAfter(license.expirationDate())) {
            problems.add("La fecha de expedicion no puede ser posterior al vencimiento");
        }
        if (!problems.isEmpty()) {
            return Result.err(problems);
        }
        employees.save(employee);
        return Result.ok(employee);
    }

    public Result<Void> setStatus(long id, EmployeeStatus status) {
        if (!Session.has(Permissions.OPERATORS_WRITE)) {
            return Result.err("No tiene permiso para modificar operadores");
        }
        employees.updateStatus(id, status);
        return Result.ok(null);
    }

    public List<Employee> withExpiringLicenses(int days, LocalDate today) {
        return employees.search(null).stream()
                .filter(employee -> LicenseRules.expiresWithin(employee.license(), days, today)
                        || LicenseRules.isExpired(employee.license(), today))
                .toList();
    }
}
