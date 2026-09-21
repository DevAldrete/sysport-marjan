package mx.marjan.fleet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import mx.marjan.shared.Database;

public class FuelLoadRepository {

    private static final String BASE = """
            SELECT f.id, f.vehicle_id, f.trip_id, f.fuel_station, f.load_date, f.liters,
                   f.price_per_liter, f.amount, f.odometer_reading,
                   CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label,
                   sr.folio
            FROM fuel_loads f
            JOIN vehicles v ON v.id = f.vehicle_id
            LEFT JOIN trips t ON t.id = f.trip_id
            LEFT JOIN service_requests sr ON sr.id = t.service_request_id
            """;

    private FuelLoad map(ResultSet rs) throws SQLException {
        Long tripId = rs.getObject("trip_id", Long.class);
        return new FuelLoad(
                rs.getLong("id"),
                rs.getLong("vehicle_id"),
                rs.getString("vehicle_label"),
                tripId,
                rs.getString("folio"),
                rs.getString("fuel_station"),
                rs.getObject("load_date", LocalDateTime.class),
                rs.getBigDecimal("liters"),
                rs.getBigDecimal("price_per_liter"),
                rs.getBigDecimal("amount"),
                rs.getBigDecimal("odometer_reading"));
    }

    public List<FuelLoad> listByVehicle(long vehicleId) {
        return Database.queryList(BASE + " WHERE f.vehicle_id = ? ORDER BY f.load_date DESC",
                this::map, vehicleId);
    }

    public List<FuelLoad> listByTrip(long tripId) {
        return Database.queryList(BASE + " WHERE f.trip_id = ? ORDER BY f.load_date", this::map, tripId);
    }

    public List<FuelLoad> listAll() {
        return Database.queryList(BASE + " ORDER BY f.load_date DESC", this::map);
    }

    public java.util.OptionalLong vehicleIdForTrip(long tripId) {
        java.util.Optional<Long> id = Database.queryOne(
                "SELECT vehicle_id FROM trips WHERE id = ?", rs -> rs.getLong("vehicle_id"), tripId);
        return id.isPresent() ? java.util.OptionalLong.of(id.get()) : java.util.OptionalLong.empty();
    }

    public java.math.BigDecimal sumByTrip(long tripId) {
        return Database.queryOne(
                "SELECT COALESCE(SUM(amount), 0) AS total FROM fuel_loads WHERE trip_id = ?",
                rs -> rs.getBigDecimal("total"), tripId).orElse(java.math.BigDecimal.ZERO);
    }

    public long insert(java.sql.Connection connection, FuelLoad load) throws SQLException {
        return Database.insertReturningId(connection, """
                INSERT INTO fuel_loads
                  (vehicle_id, trip_id, fuel_station, load_date, liters, price_per_liter,
                   amount, odometer_reading)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                load.vehicleId(), load.tripId(), load.fuelStation(), load.loadDate(),
                load.liters(), load.pricePerLiter(), load.amount(), load.odometerReading());
    }
}
