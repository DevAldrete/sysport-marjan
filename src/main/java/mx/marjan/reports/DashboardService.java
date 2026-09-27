package mx.marjan.reports;

import java.time.LocalDate;

/** FR-DSH-1: counters for the home dashboard, computed by the database. */
public class DashboardService {

    private final DashboardRepository repository = new DashboardRepository();

    public DashboardAlerts alerts(LocalDate today) {
        return repository.alerts(today);
    }
}
