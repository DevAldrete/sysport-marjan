package mx.marjan.finance;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import mx.marjan.shared.Database;

public class AdvanceRepository {

    private static final String BASE = """
            SELECT a.id, a.trip_id, sr.folio, a.employee_id, e.name AS employee_name,
                   a.amount_given, a.delivered_date, a.status, a.settled_at
            FROM advances a
            JOIN trips t ON t.id = a.trip_id
            JOIN service_requests sr ON sr.id = t.service_request_id
            JOIN employees e ON e.id = a.employee_id
            """;

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
        return Database.queryList(BASE + " WHERE a.trip_id = ? ORDER BY a.delivered_date", this::map, tripId);
    }

    public List<Advance> listAll() {
        return Database.queryList(BASE + " ORDER BY a.delivered_date DESC", this::map);
    }

    public long insert(Connection connection, Advance advance, long userId) throws SQLException {
        long id = mx.marjan.shared.Sequences.next(connection, "advances");
        Database.update(connection, """
                INSERT INTO advances (id, trip_id, employee_id, amount_given, delivered_date, status, created_by)
                VALUES (?, ?, ?, ?, ?, 'pending', ?)
                """, id, advance.tripId(), advance.employeeId(), advance.amountGiven(),
                advance.deliveredDate(), userId);
        return id;
    }

    public void delete(long id) {
        Database.update("DELETE FROM advances WHERE id = ?", id);
    }

    public void markSettled(Connection connection, long id, long userId) throws SQLException {
        Database.update(connection, """
                UPDATE advances SET status = 'settled', settled_at = ?, settled_by = ?
                WHERE id = ?
                """, LocalDateTime.now(), userId, id);
    }

    public void markSettled(long id, long userId) {
        Database.update("""
                UPDATE advances SET status = 'settled', settled_at = ?, settled_by = ?
                WHERE id = ?
                """, LocalDateTime.now(), userId, id);
    }
}
