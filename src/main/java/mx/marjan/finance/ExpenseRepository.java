package mx.marjan.finance;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the expense stored procedures. */
public class ExpenseRepository {

    private Expense map(ResultSet rs) throws SQLException {
        return new Expense(
                rs.getLong("id"),
                rs.getLong("trip_id"),
                rs.getString("folio"),
                ExpenseType.fromDb(rs.getString("expense_type")),
                rs.getBigDecimal("amount"),
                rs.getObject("expense_date", LocalDate.class),
                rs.getString("description"));
    }

    public List<Expense> listByTrip(long tripId) {
        return Database.callList("{call sp_expenses_by_trip(?)}", this::map, tripId);
    }

    public Result<Long> save(Expense expense, long userId) {
        return Database.callForId("{call sp_expense_save(?,?,?,?,?,?,?,?)}",
                expense.tripId(), expense.type().dbValue(), expense.amount(),
                expense.expenseDate(), expense.description(), userId);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_expense_delete(?)}", id);
    }
}
