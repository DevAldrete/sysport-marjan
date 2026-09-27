package mx.marjan.reports;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.shared.DataException;
import mx.marjan.shared.Database;

/** Every report is a stored procedure returning a displayable/exportable result set. */
public class ReportRepository {

    public Report revenueByClient(LocalDate from, LocalDate to) {
        return query("Ingresos por cliente", "{call sp_revenue_by_client(?,?)}", from, to);
    }

    public Report routeUsage(LocalDate from, LocalDate to) {
        return query("Rutas mas utilizadas", "{call sp_route_usage(?,?)}", from, to);
    }

    public Report vehicleUsage(LocalDate from, LocalDate to) {
        return query("Viajes por unidad", "{call sp_vehicle_usage(?,?)}", from, to);
    }

    public Report fuelEfficiency(LocalDate from, LocalDate to) {
        return query("Rendimiento de combustible", "{call sp_fuel_efficiency(?,?)}", from, to);
    }

    public Report profitability(LocalDate from, LocalDate to) {
        return query("Rentabilidad por viaje", "{call sp_profitability(?,?)}", from, to);
    }

    public Report receivables() {
        return query("Saldos por cobrar", "{call sp_receivables()}");
    }

    public Report expiringLicenses(LocalDate today) {
        return query("Licencias por vencer", "{call sp_expiring_licenses(?)}", today);
    }

    public Report maintenanceDue(LocalDate today) {
        return query("Mantenimiento proximo", "{call sp_maintenance_due(?)}", today);
    }

    private Report query(String title, String callSql, Object... params) {
        return Database.callReport(rs -> read(title, rs), callSql, params);
    }

    private Report read(String title, ResultSet rs) {
        try {
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
        } catch (SQLException failure) {
            throw new DataException(Database.translate(failure), failure);
        }
    }
}
