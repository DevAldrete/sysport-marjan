package mx.marjan.finance;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.shared.Database;

public class ExpenseRepository {

    private static final String BASE = """
            SELECT e.id, e.trip_id, sr.folio, e.expense_type, e.amount, e.expense_date, e.description
            FROM expenses e
            JOIN trips t ON t.id = e.trip_id
            JOIN service_requests sr ON sr.id = t.service_request_id
            """;

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
        return Database.queryList(BASE + " WHERE e.trip_id = ? ORDER BY e.expense_date", this::map, tripId);
    }

    public List<Expense> listAll() {
        return Database.queryList(BASE + " ORDER BY e.expense_date DESC", this::map);
    }

    public BigDecimal sumByTrip(long tripId) {
        return Database.queryOne(
                "SELECT COALESCE(SUM(amount), 0) AS total FROM expenses WHERE trip_id = ?",
                rs -> rs.getBigDecimal("total"), tripId).orElse(BigDecimal.ZERO);
    }

    public long insert(Connection connection, Expense expense, long userId) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "expenses");
        Database.update(connection, """
                INSERT INTO expenses (id, trip_id, expense_type, amount, expense_date, description, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, expense.tripId(), expense.type().dbValue(), expense.amount(),
                expense.expenseDate(), expense.description(), userId);
        return id;
    }

    public void delete(long id) {
        Database.update("DELETE FROM expenses WHERE id = ?", id);
    }
}
