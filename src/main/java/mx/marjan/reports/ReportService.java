package mx.marjan.reports;

import java.time.LocalDate;
import java.util.function.Supplier;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

/** FR-RPT-1..7: report use cases. The permission check lives here, not in the view. */
public class ReportService {

    private final ReportRepository reports = new ReportRepository();

    public Result<Report> revenueByClient(LocalDate from, LocalDate to) {
        return run(() -> reports.revenueByClient(from, to));
    }

    public Result<Report> routeUsage(LocalDate from, LocalDate to) {
        return run(() -> reports.routeUsage(from, to));
    }

    public Result<Report> vehicleUsage(LocalDate from, LocalDate to) {
        return run(() -> reports.vehicleUsage(from, to));
    }

    public Result<Report> fuelEfficiency(LocalDate from, LocalDate to) {
        return run(() -> reports.fuelEfficiency(from, to));
    }

    public Result<Report> profitability(LocalDate from, LocalDate to) {
        return run(() -> reports.profitability(from, to));
    }

    public Result<Report> receivables() {
        return run(reports::receivables);
    }

    public Result<Report> expiringLicenses(LocalDate today) {
        return run(() -> reports.expiringLicenses(today));
    }

    public Result<Report> maintenanceDue(LocalDate today) {
        return run(() -> reports.maintenanceDue(today));
    }

    private Result<Report> run(Supplier<Report> query) {
        if (!Session.has(Permissions.REPORTS_VIEW)) {
            return Result.err("No tiene permiso para ver reportes");
        }
        return Result.ok(query.get());
    }
}
