package mx.marjan.operators;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.shared.Database;

public class EmployeeRepository {

    private static final String BASE = """
            SELECT e.id, e.name, e.address, e.phone, e.email, e.rfc, e.curp,
                   e.emergency_contact_name, e.emergency_contact_phone, e.status,
                   l.id AS license_id, l.license_number, l.license_type,
                   l.issue_date, l.expiration_date
            FROM employees e
            LEFT JOIN licenses l ON l.id = e.license_id
            """;

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
        if (term == null || term.isBlank()) {
            return Database.queryList(BASE + " ORDER BY e.name", this::map);
        }
        String like = "%" + term.trim() + "%";
        return Database.queryList(BASE + " WHERE e.name LIKE ? ORDER BY e.name", this::map, like);
    }

    public Optional<Employee> findById(long id) {
        return Database.queryOne(BASE + " WHERE e.id = ?", this::map, id);
    }

    public Optional<Employee> findById(java.sql.Connection connection, long id) throws SQLException {
        return Database.queryOne(connection, BASE + " WHERE e.id = ?", this::map, id);
    }

    public List<Employee> listAssignable() {
        return Database.queryList(BASE + " WHERE e.status = 'available' ORDER BY e.name", this::map);
    }

    /** FR-TRP-1: available operators with a license valid through the window and no overlapping trip. */
    public List<Employee> listEligible(java.time.LocalDateTime start, java.time.LocalDateTime end) {
        return Database.queryList(BASE + """
                WHERE e.status = 'available'
                  AND l.expiration_date IS NOT NULL
                  AND l.expiration_date >= DATE(?)
                  AND NOT EXISTS (
                    SELECT 1 FROM trips t
                    WHERE t.employee_id = e.id
                      AND t.status IN ('scheduled', 'in_transit')
                      AND t.planned_start < ? AND t.planned_end > ?)
                ORDER BY e.name
                """, this::map, end, end, start);
    }

    public List<Employee> listByStatus(EmployeeStatus status) {
        return Database.queryList(BASE + " WHERE e.status = ? ORDER BY e.name", this::map, status.dbValue());
    }

    public void save(Employee employee) {
        Database.inTransaction(connection -> {
            Long licenseId = saveLicense(connection, employee.license());
            if (employee.id() == 0) {
                long id = mx.marjan.shared.Sequences.next(connection, "employees");
                Database.update(connection, """
                        INSERT INTO employees
                          (id, name, address, phone, email, rfc, curp,
                           emergency_contact_name, emergency_contact_phone, license_id, status)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        id, employee.name(), employee.address(), employee.phone(), employee.email(),
                        employee.rfc(), employee.curp(), employee.emergencyContactName(),
                        employee.emergencyContactPhone(), licenseId, employee.status().dbValue());
            } else {
                Database.update(connection, """
                        UPDATE employees SET
                          name = ?, address = ?, phone = ?, email = ?, rfc = ?, curp = ?,
                          emergency_contact_name = ?, emergency_contact_phone = ?, license_id = ?, status = ?
                        WHERE id = ?
                        """,
                        employee.name(), employee.address(), employee.phone(), employee.email(),
                        employee.rfc(), employee.curp(), employee.emergencyContactName(),
                        employee.emergencyContactPhone(), licenseId, employee.status().dbValue(),
                        employee.id());
            }
            return null;
        });
    }

    private Long saveLicense(Connection connection, License license) throws SQLException {
        if (license == null) {
            return null;
        }
        if (license.id() == 0) {
            long id = mx.marjan.shared.Sequences.next(connection, "licenses");
            Database.update(connection, """
                    INSERT INTO licenses (id, license_number, license_type, issue_date, expiration_date)
                    VALUES (?, ?, ?, ?, ?)
                    """, id, license.licenseNumber(), license.licenseType(),
                    license.issueDate(), license.expirationDate());
            return id;
        }
        Database.update(connection, """
                UPDATE licenses SET license_number = ?, license_type = ?, issue_date = ?, expiration_date = ?
                WHERE id = ?
                """, license.licenseNumber(), license.licenseType(),
                license.issueDate(), license.expirationDate(), license.id());
        return license.id();
    }

    public void delete(long id) {
        Database.inTransaction(connection -> {
            Optional<Employee> employee = findById(connection, id);
            Database.update(connection, "DELETE FROM employees WHERE id = ?", id);
            if (employee.isPresent() && employee.get().license() != null) {
                Database.update(connection, "DELETE FROM licenses WHERE id = ?",
                        employee.get().license().id());
            }
            return null;
        });
    }

    public void updateStatus(long id, EmployeeStatus status) {
        Database.update("UPDATE employees SET status = ? WHERE id = ?", status.dbValue(), id);
    }

    public void updateStatus(java.sql.Connection connection, long id, EmployeeStatus status) throws SQLException {
        Database.update(connection, "UPDATE employees SET status = ? WHERE id = ?", status.dbValue(), id);
    }
}
