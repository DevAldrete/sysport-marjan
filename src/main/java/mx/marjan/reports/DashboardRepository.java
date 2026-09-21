package mx.marjan.reports;

import java.time.LocalDate;
import mx.marjan.shared.Database;

public class DashboardRepository {

    public int expiringLicenses(LocalDate today) {
        return count("""
                SELECT COUNT(*) FROM employees e
                JOIN licenses l ON l.id = e.license_id
                WHERE l.expiration_date <= DATE_ADD(?, INTERVAL 30 DAY)
                """, today);
    }

    public int overdueInvoices(LocalDate today) {
        return count("""
                SELECT COUNT(*) FROM invoices i
                WHERE i.status <> 'cancelled' AND i.due_date < ?
                  AND i.amount > COALESCE(
                      (SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0)
                """, today);
    }

    public int maintenanceDue(LocalDate today) {
        return count("""
                SELECT COUNT(DISTINCT m.vehicle_id)
                FROM maintenance m JOIN vehicles v ON v.id = m.vehicle_id
                WHERE (m.next_service_date IS NOT NULL AND m.next_service_date <= ?)
                   OR (m.next_service_km IS NOT NULL AND m.next_service_km <= v.mileage)
                """, today);
    }

    public int pendingAssignments() {
        return count("SELECT COUNT(*) FROM service_requests WHERE status = 'scheduled'");
    }

    private int count(String sql, Object... params) {
        return Database.queryOne(sql, rs -> rs.getInt(1), params).orElse(0);
    }
}
