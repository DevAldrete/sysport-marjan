package mx.marjan.reports;

import mx.marjan.fleet.VehicleStatus;

/** FR-DSH-6: how many vehicles are in each status. */
public record FleetStatusCount(VehicleStatus status, int count) {}
