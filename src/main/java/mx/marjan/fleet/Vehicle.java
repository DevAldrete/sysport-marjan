package mx.marjan.fleet;

import java.math.BigDecimal;

public record Vehicle(
        long id,
        String internalCode,
        String plates,
        String brand,
        String model,
        Integer year,
        String serialNumber,
        String vehicleType,
        BigDecimal loadCapacity,
        BigDecimal mileage,
        VehicleStatus status) {

    public static Vehicle empty() {
        return new Vehicle(0, "", "", "", "", null, "", "", BigDecimal.ZERO, BigDecimal.ZERO,
                VehicleStatus.AVAILABLE);
    }

    public String label() {
        return internalCode + " (" + plates + ")";
    }

    @Override
    public String toString() {
        return label();
    }
}
