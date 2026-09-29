package mx.marjan.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

/** FR-DSH-1..6: dashboard use cases. Permission checks live here, not in the view. */
public class DashboardService {

    private final DashboardRepository repository = new DashboardRepository();

    /** FR-DSH-1: the four alert counters. Always available, no permission required. */
    public DashboardAlerts alerts(LocalDate today) {
        return repository.alerts(today);
    }

    public Result<DashboardFinance> finance(LocalDate today) {
        return run(Permissions.INVOICES_READ, () -> repository.finance(today));
    }

    public Result<DashboardOperations> operations(LocalDate today) {
        if (!Session.has(Permissions.TRIPS_READ) && !Session.has(Permissions.FLEET_READ)) {
            return Result.err("No tiene permiso para ver la operacion");
        }
        return Result.ok(repository.operations(today));
    }

    public Result<List<UpcomingTrip>> upcomingTrips(LocalDate today, int days) {
        return run(Permissions.TRIPS_READ, () -> repository.upcomingTrips(today, days));
    }

    public Result<List<Debtor>> topDebtors(int limit) {
        return run(Permissions.INVOICES_READ, () -> repository.topDebtors(limit));
    }

    public Result<List<MonthlyRevenue>> monthlyRevenue(int months) {
        return run(Permissions.REPORTS_VIEW, () -> repository.monthlyRevenue(months));
    }

    public Result<List<FleetStatusCount>> fleetStatus() {
        return run(Permissions.FLEET_READ, repository::fleetStatus);
    }

    private <T> Result<T> run(String permission, Supplier<T> query) {
        if (!Session.has(permission)) {
            return Result.err("No tiene permiso para ver esta informacion");
        }
        return Result.ok(query.get());
    }
}
