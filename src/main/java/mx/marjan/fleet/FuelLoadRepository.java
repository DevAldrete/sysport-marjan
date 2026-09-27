package mx.marjan.fleet;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import mx.marjan.shared.Database;
import mx.marjan.shared.Result;

/** Thin JDBC wrapper over the fuel-load stored procedures. */
public class FuelLoadRepository {

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
        return Database.callList("{call sp_fuel_by_vehicle(?)}", this::map, vehicleId);
    }

    public List<FuelLoad> listByTrip(long tripId) {
        return Database.callList("{call sp_fuel_by_trip(?)}", this::map, tripId);
    }

    public List<FuelLoad> listAll() {
        return Database.callList("{call sp_fuel_list()}", this::map);
    }

    public java.util.OptionalLong vehicleIdForTrip(long tripId) {
        return Database.callOne("{call sp_trip_vehicle(?)}",
                rs -> rs.getLong("vehicle_id"), tripId)
                .map(java.util.OptionalLong::of).orElse(java.util.OptionalLong.empty());
    }

    public java.math.BigDecimal sumByTrip(long tripId) {
        return Database.callOne("{call sp_fuel_sum_by_trip(?)}",
                rs -> rs.getBigDecimal("total"), tripId).orElse(java.math.BigDecimal.ZERO);
    }

    public Result<Long> save(FuelLoad load, long userId) {
        return Database.callForId("{call sp_fuel_save(?,?,?,?,?,?,?,?,?,?,?)}",
                load.vehicleId(), load.tripId(), load.fuelStation(), load.loadDate(),
                load.liters(), load.pricePerLiter(), load.amount(), load.odometerReading(), userId);
    }

    public void delete(long id) {
        Database.callNoOut("{call sp_fuel_delete(?)}", id);
    }
}
