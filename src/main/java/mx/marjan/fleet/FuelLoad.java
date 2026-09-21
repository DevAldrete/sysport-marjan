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

    public static FuelLoad empty() {
        return new FuelLoad(0, 0, "", null, "", "", LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }
}
