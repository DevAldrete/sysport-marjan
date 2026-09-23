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
        if (!mx.marjan.shared.Validators.isLiters(load.liters())) {
            problems.add("Los litros son invalidos o exceden el maximo permitido");
        }
        if (!mx.marjan.shared.Validators.isPricePerLiter(load.pricePerLiter())) {
            problems.add("El precio por litro es invalido o excede el maximo permitido");
        }
        if (load.amount() == null || load.amount().signum() <= 0
                || !mx.marjan.shared.Validators.isMoney(load.amount())) {
            problems.add("El importe debe ser mayor a cero y dentro del rango permitido");
        }
        if (!mx.marjan.shared.Validators.isMeasure(load.odometerReading())) {
            problems.add("El odometro es invalido o excede el maximo permitido");
        }
        if (!mx.marjan.shared.Validators.isValidDate(
                load.loadDate() == null ? null : load.loadDate().toLocalDate())) {
            problems.add("La fecha de la carga no es valida");
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
}
