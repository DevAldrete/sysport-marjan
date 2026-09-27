package mx.marjan.operators;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the employee stored procedures. */
public class EmployeeRepository {

    private Employee map(ResultSet rs) throws SQLException {
        Long licenseId = rs.getObject("license_id", Long.class);
        License license = licenseId == null ? null : new License(
                licenseId,
                rs.getString("license_number"),
                rs.getString("license_type"),
                rs.getObject("issue_date", LocalDate.class),
                rs.getObject("expiration_date", LocalDate.class));
        return new Employee(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("address"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getString("rfc"),
                rs.getString("curp"),
                rs.getString("emergency_contact_name"),
                rs.getString("emergency_contact_phone"),
                license,
                EmployeeStatus.fromDb(rs.getString("status")));
    }

    public List<Employee> search(String term) {
        String value = term == null || term.isBlank() ? null : term.trim();
        return Database.callList("{call sp_employees_search(?)}", this::map, value);
    }

    public Optional<Employee> findById(long id) {
        return Database.callOne("{call sp_employee_by_id(?)}", this::map, id);
    }

    public List<Employee> listAssignable() {
        return Database.callList("{call sp_employees_assignable()}", this::map);
    }

    /** FR-TRP-1: available operators with a license valid through the window and no overlapping trip. */
    public List<Employee> listEligible(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return Database.callList("{call sp_eligible_operators_full(?,?)}", this::map, start, end);
    }

    public Result<Long> save(Employee employee) {
        License license = employee.license();
        return Database.callForId("{call sp_employee_save(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)}",
                employee.id(), employee.name(), employee.address(), employee.phone(),
                employee.email(), employee.rfc(), employee.curp(), employee.emergencyContactName(),
                employee.emergencyContactPhone(),
                license == null ? null : license.id(),
                license == null ? null : license.licenseNumber(),
                license == null ? null : license.licenseType(),
                license == null ? null : license.issueDate(),
                license == null ? null : license.expirationDate(),
                employee.status().dbValue());
    }

    public Result<Void> delete(long id) {
        return Database.callVoid("{call sp_employee_delete(?,?)}", id);
    }

    public Result<Void> setStatus(long id, EmployeeStatus status) {
        Database.callNoOut("{call sp_employee_set_status(?,?)}", id, status.dbValue());
        return Result.ok(null);
    }
}
