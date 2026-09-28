package mx.marjan.fleet;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Maintenance(
        long id,
        long vehicleId,
        String vehicleLabel,
        LocalDate maintenanceDate,
        BigDecimal odometerReading,
        MaintenanceType type,
        String workPerformed,
        String provider,
        BigDecimal cost,
        LocalDate nextServiceDate,
        BigDecimal nextServiceKm) {
}
