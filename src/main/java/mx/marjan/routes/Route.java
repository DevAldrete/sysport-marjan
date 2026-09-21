package mx.marjan.routes;

import java.math.BigDecimal;

public record Route(long id, String origin, String destination, BigDecimal estimatedKm, String description) {

    public static Route empty() {
        return new Route(0, "", "", BigDecimal.ZERO, "");
    }

    public String label() {
        return origin + " -> " + destination;
    }

    @Override
    public String toString() {
        return label();
    }
}
