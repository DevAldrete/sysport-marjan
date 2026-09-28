package mx.marjan.routes;

/**
 * One ordered stop of a route. {@code id == 0} means a new stop that the
 * repository will insert; the sequence is the position in the route.
 */
public record RouteStop(long id, long routeId, int sequenceNo, String location) {

    public static RouteStop empty() {
        return new RouteStop(0, 0, 0, "");
    }

    @Override
    public String toString() {
        return location;
    }
}
