package mx.marjan.fleet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import mx.marjan.shared.Result;

/** BR-18: fuel amount consistency, odometer monotonicity, vehicle/trip match. */
public final class FuelRules {

    private static final BigDecimal TOLERANCE = new BigDecimal("0.05");

    private FuelRules() {}

    public static Result<Void> validate(FuelLoad load, BigDecimal currentMileage) {
        List<String> problems = new ArrayList<>();
        if (load.liters() == null || load.liters().signum() <= 0) {
            problems.add("Los litros deben ser mayores a cero");
        }
        if (load.pricePerLiter() == null || load.pricePerLiter().signum() <= 0) {
            problems.add("El precio por litro debe ser mayor a cero");
        }
        if (load.amount() == null || load.amount().signum() <= 0) {
            problems.add("El importe debe ser mayor a cero");
        }
        if (load.liters() != null && load.pricePerLiter() != null && load.amount() != null) {
            BigDecimal expected = load.liters().multiply(load.pricePerLiter());
            if (expected.subtract(load.amount()).abs().compareTo(TOLERANCE) > 0) {
                problems.add("El importe no coincide con litros x precio (esperado "
                        + expected.setScale(2, RoundingMode.HALF_UP) + ")");
            }
        }
        if (load.odometerReading() != null && currentMileage != null
                && load.odometerReading().compareTo(currentMileage) < 0) {
            problems.add("El odometro no puede ser menor al kilometraje actual de la unidad");
        }
        return problems.isEmpty() ? Result.ok(null) : Result.err(problems);
    }

    /** The trip's vehicle must be the same as the load's vehicle. */
    public static Result<Void> validateTripVehicle(FuelLoad load, Long tripVehicleId) {
        if (load.tripId() != null && tripVehicleId != null && tripVehicleId != load.vehicleId()) {
            return Result.err("La unidad de la carga no coincide con la unidad del viaje");
        }
        return Result.ok(null);
    }

    public static BigDecimal kmPerLiter(BigDecimal km, BigDecimal liters) {
        if (km == null || liters == null || liters.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return km.divide(liters, 2, RoundingMode.HALF_UP);
    }
}
