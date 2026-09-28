package mx.marjan.routes;

import java.math.BigDecimal;

/**
 * A route's header: origin and destination are the first and last stop
 * snapshots, {@code label} is the full path ("A -> B -> C") computed by the
 * database from {@link RouteStop}s.
 */
public record Route(long id, String origin, String destination, BigDecimal estimatedKm,
        String description, String label) {

    public static Route empty() {
        return new Route(0, "", "", BigDecimal.ZERO, "", "");
    }

    public String label() {
        if (label != null && !label.isBlank()) {
            return label;
        }
        return origin + " -> " + destination;
    }

    @Override
    public String toString() {
        return label();
    }
}
