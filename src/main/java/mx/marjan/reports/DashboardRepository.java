package mx.marjan.reports;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import mx.marjan.fleet.VehicleStatus;
import mx.marjan.shared.Database;
import mx.marjan.trips.TripStatus;

/** FR-DSH-1..6: dashboard queries, all computed by the database. */
public class DashboardRepository {

    public DashboardAlerts alerts(LocalDate today) {
        Object[] out = Database.call("{call sp_dashboard(?,?,?,?,?)}",
                new int[] { Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER }, today);
        return new DashboardAlerts(intValue(out[0]), intValue(out[1]), intValue(out[2]), intValue(out[3]));
    }

    public DashboardFinance finance(LocalDate today) {
        return Database.callOne("{call sp_dashboard_finance(?)}", rs -> new DashboardFinance(
                rs.getBigDecimal("ingresos_mes"),
                rs.getBigDecimal("cobrado_mes"),
                rs.getBigDecimal("por_cobrar"),
                rs.getBigDecimal("vencido")), today)
                .orElseGet(() -> new DashboardFinance(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    public DashboardOperations operations(LocalDate today) {
        Object[] out = Database.call("{call sp_dashboard_operations(?,?,?)}",
                new int[] { Types.INTEGER, Types.INTEGER }, today);
        return new DashboardOperations(intValue(out[0]), intValue(out[1]));
    }

    public List<UpcomingTrip> upcomingTrips(LocalDate today, int days) {
        return Database.callList("{call sp_dashboard_upcoming_trips(?,?)}", rs -> new UpcomingTrip(
                rs.getLong("id"),
                rs.getString("folio"),
                rs.getString("cliente"),
                rs.getString("ruta"),
                rs.getString("unidad"),
                rs.getString("operador"),
                rs.getObject("salida", LocalDateTime.class),
                TripStatus.fromDb(rs.getString("status"))), today, days);
    }

    public List<Debtor> topDebtors(int limit) {
        return Database.callList("{call sp_dashboard_top_debtors(?)}", rs -> new Debtor(
                rs.getString("cliente"),
                rs.getBigDecimal("saldo"),
                rs.getInt("facturas"),
                rs.getObject("vencimiento_mas_antiguo", LocalDate.class)), limit);
    }

    public List<MonthlyRevenue> monthlyRevenue(int months) {
        return Database.callList("{call sp_dashboard_monthly_revenue(?)}", rs -> new MonthlyRevenue(
                rs.getString("mes"),
                rs.getBigDecimal("ingresos"),
                rs.getBigDecimal("costo"),
                rs.getBigDecimal("margen")), months);
    }

    public List<FleetStatusCount> fleetStatus() {
        return Database.callList("{call sp_dashboard_fleet_status()}", rs -> new FleetStatusCount(
                VehicleStatus.fromDb(rs.getString("status")),
                rs.getInt("unidades")));
    }

    private static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
