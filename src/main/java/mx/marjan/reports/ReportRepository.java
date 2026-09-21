package mx.marjan.reports;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.shared.Database;

/** Hand-written report SQL. Every method returns a displayable/exportable Report. */
public class ReportRepository {

    public Report revenueByClient(LocalDate from, LocalDate to) {
        return query("Ingresos por cliente", """
                SELECT c.name AS cliente,
                       COUNT(DISTINCT sr.id) AS solicitudes,
                       COALESCE(SUM(sr.agreed_rate), 0) AS facturado,
                       COALESCE(SUM(p.paid), 0) AS cobrado
                FROM clients c
                LEFT JOIN service_requests sr
                       ON sr.client_id = c.id
                      AND sr.pickup_date_scheduled BETWEEN ? AND ?
                LEFT JOIN (SELECT invoice_id, SUM(amount) AS paid FROM payments GROUP BY invoice_id) p
                       ON p.invoice_id = (SELECT i.id FROM invoices i WHERE i.service_request_id = sr.id)
                GROUP BY c.id, c.name
                ORDER BY facturado DESC
                """, from.atStartOfDay(), to.atTime(23, 59, 59));
    }

    public Report routeUsage(LocalDate from, LocalDate to) {
        return query("Rutas mas utilizadas", """
                SELECT CONCAT(r.origin, ' -> ', r.destination) AS ruta,
                       COUNT(t.id) AS viajes,
                       COALESCE(SUM(sr.agreed_rate), 0) AS ingresos
                FROM routes r
                LEFT JOIN service_requests sr
                       ON sr.route_id = r.id
                      AND sr.pickup_date_scheduled BETWEEN ? AND ?
                LEFT JOIN trips t ON t.service_request_id = sr.id
                GROUP BY r.id, r.origin, r.destination
                ORDER BY viajes DESC
                """, from.atStartOfDay(), to.atTime(23, 59, 59));
    }

    public Report vehicleUsage(LocalDate from, LocalDate to) {
        return query("Viajes por unidad", """
                SELECT v.internal_code AS unidad, v.plates AS placas,
                       COUNT(t.id) AS viajes,
                       COALESCE(SUM(t.actual_km), 0) AS km
                FROM vehicles v
                LEFT JOIN trips t ON t.vehicle_id = v.id
                       AND t.planned_start BETWEEN ? AND ?
                GROUP BY v.id, v.internal_code, v.plates
                ORDER BY viajes DESC
                """, from.atStartOfDay(), to.atTime(23, 59, 59));
    }

    public Report fuelEfficiency(LocalDate from, LocalDate to) {
        return query("Rendimiento de combustible", """
                SELECT v.internal_code AS unidad,
                       COALESCE(MAX(f.odometer_reading) - MIN(f.odometer_reading), 0) AS km,
                       COALESCE(SUM(f.liters), 0) AS litros,
                       ROUND(COALESCE(MAX(f.odometer_reading) - MIN(f.odometer_reading), 0)
                             / NULLIF(SUM(f.liters), 0), 2) AS km_por_litro
                FROM vehicles v
                JOIN fuel_loads f ON f.vehicle_id = v.id
                       AND f.load_date BETWEEN ? AND ?
                GROUP BY v.id, v.internal_code
                ORDER BY km_por_litro DESC
                """, from.atStartOfDay(), to.atTime(23, 59, 59));
    }

    /** FR-RPT-4: revenue - expenses - fuel per trip (aggregates in subqueries, PRD 8.4). */
    public Report profitability(LocalDate from, LocalDate to) {
        return query("Rentabilidad por viaje", """
                SELECT sr.folio, c.name AS cliente,
                       sr.agreed_rate AS ingreso,
                       COALESCE(e.total, 0) + COALESCE(f.total, 0) AS costo,
                       sr.agreed_rate - COALESCE(e.total, 0) - COALESCE(f.total, 0) AS margen
                FROM service_requests sr
                JOIN clients c ON c.id = sr.client_id
                JOIN trips t ON t.service_request_id = sr.id
                LEFT JOIN (SELECT trip_id, SUM(amount) AS total FROM expenses GROUP BY trip_id) e
                       ON e.trip_id = t.id
                LEFT JOIN (SELECT trip_id, SUM(amount) AS total FROM fuel_loads GROUP BY trip_id) f
                       ON f.trip_id = t.id
                WHERE t.planned_start BETWEEN ? AND ?
                ORDER BY margen DESC
                """, from.atStartOfDay(), to.atTime(23, 59, 59));
    }

    public Report receivables() {
        return query("Saldos por cobrar", """
                SELECT c.name AS cliente, i.invoice_number AS factura,
                       i.amount AS importe, COALESCE(p.paid, 0) AS pagado,
                       i.amount - COALESCE(p.paid, 0) AS saldo, i.due_date AS vencimiento
                FROM invoices i
                JOIN clients c ON c.id = i.client_id
                LEFT JOIN (SELECT invoice_id, SUM(amount) AS paid FROM payments GROUP BY invoice_id) p
                       ON p.invoice_id = i.id
                WHERE i.status <> 'cancelled' AND i.amount > COALESCE(p.paid, 0)
                ORDER BY i.due_date
                """);
    }

    public Report expiringLicenses(LocalDate today) {
        return query("Licencias por vencer", """
                SELECT e.name AS operador, l.license_number AS licencia,
                       l.expiration_date AS vence
                FROM employees e
                JOIN licenses l ON l.id = e.license_id
                WHERE l.expiration_date <= DATE_ADD(?, INTERVAL 30 DAY)
                ORDER BY l.expiration_date
                """, today);
    }

    public Report maintenanceDue(LocalDate today) {
        return query("Mantenimiento proximo", """
                SELECT v.internal_code AS unidad, v.mileage AS kilometraje,
                       m.next_service_date AS proxima_fecha, m.next_service_km AS proximo_km
                FROM maintenance m
                JOIN vehicles v ON v.id = m.vehicle_id
                WHERE (m.next_service_date IS NOT NULL AND m.next_service_date <= ?)
                   OR (m.next_service_km IS NOT NULL AND m.next_service_km <= v.mileage)
                ORDER BY m.next_service_date
                """, today);
    }

    private Report query(String title, String sql, Object... params) {
        try (Connection connection = Database.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            Database.bind(statement, params);
            try (ResultSet rs = statement.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                List<String> headers = new ArrayList<>();
                for (int i = 1; i <= columnCount; i++) {
                    headers.add(meta.getColumnLabel(i));
                }
                List<List<Object>> rows = new ArrayList<>();
                while (rs.next()) {
                    List<Object> row = new ArrayList<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }
                return new Report(title, headers, rows);
            }
        } catch (SQLException failure) {
            throw new RuntimeException(failure.getMessage(), failure);
        }
    }
}
