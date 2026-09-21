package mx.marjan.reports;

import java.time.LocalDate;

/** FR-DSH-1: counters for the home dashboard. */
public class DashboardService {

    private final DashboardRepository repository = new DashboardRepository();

    public DashboardAlerts alerts(LocalDate today) {
        return new DashboardAlerts(
                repository.expiringLicenses(today),
                repository.overdueInvoices(today),
                repository.maintenanceDue(today),
                repository.pendingAssignments());
    }
}
