package mx.marjan.api.jobs;

import io.micronaut.context.annotation.Value;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import mx.marjan.api.security.SystemCaller;
import mx.marjan.finance.InvoiceService;
import mx.marjan.security.Caller;
import mx.marjan.shared.Dates;
import mx.marjan.trips.TripService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Time-driven reconciliation that the desktop app ran on a UI timer. It now
 * runs server-side so GET requests stay side-effect free. The system user id
 * (default 1, admin) is written to the audit trail.
 */
@Singleton
public class LifecycleJobs {

    private static final Logger LOG = LoggerFactory.getLogger(LifecycleJobs.class);

    private final TripService trips;
    private final InvoiceService invoices;

    public LifecycleJobs(@Value("${sysport.jobs.system-user-id:1}") long systemUserId) {
        Caller caller = new SystemCaller(systemUserId);
        this.trips = new TripService(caller);
        this.invoices = new InvoiceService(caller);
    }

    /** Confirms scheduled dates (BR-03) and departs trips whose planned start has passed. */
    @Scheduled(fixedDelay = "60s")
    void sweepLifecycle() {
        int changed = trips.sweepLifecycle();
        if (changed > 0) {
            LOG.info("Lifecycle sweep updated {} record(s)", changed);
        }
    }

    /** BR-19: recompute paid/overdue for open invoices. */
    @Scheduled(fixedDelay = "1h")
    void refreshInvoiceStatuses() {
        int changed = invoices.refreshStatuses(Dates.today());
        if (changed > 0) {
            LOG.info("Invoice status refresh updated {} invoice(s)", changed);
        }
    }
}
