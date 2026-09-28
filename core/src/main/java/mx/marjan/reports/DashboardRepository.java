package mx.marjan.reports;

import java.sql.Types;
import java.time.LocalDate;
import mx.marjan.shared.Database;

/** FR-DSH-1: dashboard counters, computed by the database in one call. */
public class DashboardRepository {

    public DashboardAlerts alerts(LocalDate today) {
        Object[] out = Database.call("{call sp_dashboard(?,?,?,?,?)}",
                new int[] { Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.INTEGER }, today);
        return new DashboardAlerts(intValue(out[0]), intValue(out[1]), intValue(out[2]), intValue(out[3]));
    }

    private static int intValue(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }
}
