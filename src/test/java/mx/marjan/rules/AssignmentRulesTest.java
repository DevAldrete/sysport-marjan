package mx.marjan.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import mx.marjan.fleet.Vehicle;
import mx.marjan.fleet.VehicleStatus;
import mx.marjan.operators.Employee;
import mx.marjan.operators.EmployeeStatus;
import mx.marjan.operators.License;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.shared.Result;
import mx.marjan.trips.AssignmentPlan;
import mx.marjan.trips.AssignmentRules;
import mx.marjan.trips.Trip;
import mx.marjan.trips.TripStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentRulesTest {

    private final LocalDate today = LocalDate.of(2026, 1, 1);
    private final LocalDateTime pickup = LocalDateTime.of(2026, 1, 10, 8, 0);
    private final LocalDateTime delivery = LocalDateTime.of(2026, 1, 12, 18, 0);

    private ServiceRequest request(BigDecimal weight, RequestStatus status) {
        return new ServiceRequest(1, "SR-2026-000001", 1, "Cliente", 1, "A -> B",
                "Carga", weight, pickup, delivery, new BigDecimal("1000"), true, status, "", LocalDateTime.now());
    }

    private Vehicle vehicle(VehicleStatus status, BigDecimal capacity) {
        return new Vehicle(1, "ECO-01", "ABC-123", "Marca", "Modelo", 2020, "SN", "Tipo",
                capacity, BigDecimal.ZERO, status);
    }

    private Employee operator(EmployeeStatus status, LocalDate expiry) {
        return new Employee(1, "Operador", "", "555", "", "", "", "", "",
                new License(1, "LIC-1", "Federal", today, expiry), status);
    }

    private Trip activeTrip(LocalDateTime start, LocalDateTime end) {
        return new Trip(99, 2, "SR-2026-000099", "Otro", "A -> B", 1, "ECO-01",
                1, "Operador", BigDecimal.ZERO, null, start, end, null, null, TripStatus.SCHEDULED);
    }

    @Test
    void acceptsEligibleAssignment() {
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.AVAILABLE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2027, 1, 1)),
                List.of(), today);
        assertTrue(result.isOk());
    }

    @Test
    void rejectsVehicleInMaintenance() { // BR-07
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.MAINTENANCE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2027, 1, 1)),
                List.of(), today);
        assertTrue(result.isErr());
    }

    @Test
    void rejectsOutOfServiceVehicle() { // BR-11
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.OUT_OF_SERVICE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2027, 1, 1)),
                List.of(), today);
        assertTrue(result.isErr());
    }

    @Test
    void rejectsVehicleWithOverlappingTrip() { // BR-05
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.AVAILABLE, new BigDecimal("5000")),
                List.of(activeTrip(pickup.plusDays(1), delivery.plusDays(1))),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2027, 1, 1)),
                List.of(), today);
        assertTrue(result.isErr());
    }

    @Test
    void rejectsOperatorWithOverlappingTrip() { // BR-06
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.AVAILABLE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2027, 1, 1)),
                List.of(activeTrip(pickup.plusDays(1), delivery.plusDays(1))), today);
        assertTrue(result.isErr());
    }

    @Test
    void rejectsOperatorWithExpiredLicense() { // BR-09 / BR-10
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.AVAILABLE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2025, 12, 1)),
                List.of(), today);
        assertTrue(result.isErr());
    }

    @Test
    void rejectsOperatorOnVacation() { // BR-09
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.AVAILABLE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.VACATION, LocalDate.of(2027, 1, 1)),
                List.of(), today);
        assertTrue(result.isErr());
    }

    @Test
    void rejectsVehicleWithInsufficientCapacity() { // BR-08
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("9000"), RequestStatus.SCHEDULED),
                vehicle(VehicleStatus.AVAILABLE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2027, 1, 1)),
                List.of(), today);
        assertTrue(result.isErr());
    }

    @Test
    void rejectsRequestThatIsNotScheduled() { // BR-03
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("1000"), RequestStatus.REQUESTED),
                vehicle(VehicleStatus.AVAILABLE, new BigDecimal("5000")),
                List.of(),
                operator(EmployeeStatus.AVAILABLE, LocalDate.of(2027, 1, 1)),
                List.of(), today);
        assertTrue(result.isErr());
    }

    @Test
    void collectsAllProblemsAtOnce() {
        Result<AssignmentPlan> result = AssignmentRules.validate(
                request(new BigDecimal("9000"), RequestStatus.REQUESTED),
                vehicle(VehicleStatus.MAINTENANCE, new BigDecimal("5000")),
                List.of(activeTrip(pickup, delivery)),
                operator(EmployeeStatus.TERMINATED, LocalDate.of(2024, 1, 1)),
                List.of(activeTrip(pickup, delivery)), today);
        assertTrue(result.isErr());
        assertTrue(result.problems().size() >= 5);
    }

    @Test
    void detectsOverlappingWindows() {
        assertTrue(AssignmentRules.overlaps(pickup, delivery, pickup.plusDays(1), delivery.plusDays(1)));
        assertEquals(false, AssignmentRules.overlaps(pickup, delivery, delivery, delivery.plusDays(1)));
    }
}
