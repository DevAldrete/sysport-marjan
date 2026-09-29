package mx.marjan.reports;

/** FR-DSH-2: live operational counters (trips on the road, vehicles free). */
public record DashboardOperations(int activeTrips, int availableVehicles) {}
