package mx.marjan.finance;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the advance stored procedures. */
public class AdvanceRepository {

    private Advance map(ResultSet rs) throws SQLException {
        return new Advance(
                rs.getLong("id"),
                rs.getLong("trip_id"),
                rs.getString("folio"),
                rs.getLong("employee_id"),
                rs.getString("employee_name"),
                rs.getBigDecimal("amount_given"),
                rs.getObject("delivered_date", LocalDate.class),
                AdvanceStatus.fromDb(rs.getString("status")),
                rs.getObject("settled_at", LocalDateTime.class));
    }

    public List<Advance> listByTrip(long tripId) {
        return Database.callList("{call sp_advances_by_trip(?)}", this::map, tripId);
    }

    public List<Advance> listAll() {
        return Database.callList("{call sp_advances_list()}", this::map);
    }

    public Result<Long> save(Advance advance, long userId) {
        return Database.callForId("{call sp_advance_save(?,?,?,?,?,?,?)}",
                advance.tripId(), advance.employeeId(), advance.amountGiven(),
                advance.deliveredDate(), userId);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_advance_delete(?)}", id);
    }

    public Result<Void> settle(long id, long userId) {
        return Database.callVoid("{call sp_settle_advance(?,?,?)}", id, userId);
    }

    /** BR-16: given vs proven (expenses + fuel) for a trip. */
    public AdvanceBalance balance(long tripId) {
        Object[] out = Database.call("{call sp_advance_balance(?,?,?,?)}",
                new int[] { Types.DECIMAL, Types.DECIMAL, Types.DECIMAL }, tripId);
        BigDecimal given = decimal(out[0]);
        BigDecimal proven = decimal(out[1]);
        BigDecimal balance = decimal(out[2]);
        int sign = balance.signum();
        AdvanceBalance.Outcome outcome = sign > 0
                ? new AdvanceBalance.OperatorOwes(balance)
                : sign < 0 ? new AdvanceBalance.CompanyOwes(balance.negate())
                        : new AdvanceBalance.Settled();
        return new AdvanceBalance(given, proven, outcome);
    }

    private static BigDecimal decimal(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }
}
