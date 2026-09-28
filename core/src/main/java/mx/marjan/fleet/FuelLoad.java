package mx.marjan.fleet;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FuelLoad(
        long id,
        long vehicleId,
        String vehicleLabel,
        Long tripId,
        String tripLabel,
        String fuelStation,
        LocalDateTime loadDate,
        BigDecimal liters,
        BigDecimal pricePerLiter,
        BigDecimal amount,
        BigDecimal odometerReading) {
}
