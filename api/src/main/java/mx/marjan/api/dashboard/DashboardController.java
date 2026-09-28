package mx.marjan.api.dashboard;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.rules.SecurityRule;
import mx.marjan.reports.DashboardAlerts;
import mx.marjan.reports.DashboardService;
import mx.marjan.shared.Dates;

/** FR-DSH-1: the home dashboard counters, available to any authenticated user. */
@Controller("/api/dashboard")
@Secured(SecurityRule.IS_AUTHENTICATED)
public class DashboardController {

    private final DashboardService dashboard = new DashboardService();

    @Get
    public DashboardAlerts alerts() {
        return dashboard.alerts(Dates.today());
    }
}
