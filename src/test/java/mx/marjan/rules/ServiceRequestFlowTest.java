package mx.marjan.rules;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.requests.ServiceRequestFlow;
import mx.marjan.shared.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRequestFlowTest {

    private ServiceRequest request(RequestStatus status) {
        return new ServiceRequest(1, "SR-2026-000001", 1, "Cliente", 1, "A -> B", "Carga",
                BigDecimal.TEN, null, null, null, true, status, "", LocalDateTime.now());
    }

    @Test
    void authorizesWithPositiveRate() { // BR-04
        Result<ServiceRequest> result = ServiceRequestFlow.authorize(request(RequestStatus.REQUESTED),
                new BigDecimal("1500"));
        assertTrue(result.isOk());
        assertTrue(result.value().agreedRate().signum() > 0);
        assertTrue(result.value().status() == RequestStatus.AUTHORIZED);
    }

    @Test
    void rejectsAuthorizationWithoutRate() {
        assertTrue(ServiceRequestFlow.authorize(request(RequestStatus.REQUESTED), BigDecimal.ZERO).isErr());
        assertTrue(ServiceRequestFlow.authorize(request(RequestStatus.REQUESTED), null).isErr());
    }

    @Test
    void rejectsInvalidTransition() { // BR-03
        assertTrue(ServiceRequestFlow.authorize(request(RequestStatus.DELIVERED), BigDecimal.ONE).isErr());
    }

    @Test
    void schedulesWithValidDates() {
        Result<ServiceRequest> result = ServiceRequestFlow.schedule(request(RequestStatus.AUTHORIZED),
                LocalDateTime.of(2026, 1, 1, 8, 0), LocalDateTime.of(2026, 1, 2, 8, 0));
        assertTrue(result.isOk());
        assertTrue(result.value().status() == RequestStatus.SCHEDULED);
    }

    @Test
    void rejectsDeliveryBeforePickup() {
        assertTrue(ServiceRequestFlow.schedule(request(RequestStatus.AUTHORIZED),
                LocalDateTime.of(2026, 1, 2, 8, 0), LocalDateTime.of(2026, 1, 1, 8, 0)).isErr());
    }

    @Test
    void autoSchedulesAuthorizedRequestWithConfirmedDates() { // BR-03
        ServiceRequest base = request(RequestStatus.AUTHORIZED);
        ServiceRequest dated = new ServiceRequest(base.id(), base.folio(), base.clientId(),
                base.clientName(), base.routeId(), base.routeLabel(), base.cargoDescription(),
                base.estimatedWeight(), LocalDateTime.of(2026, 1, 1, 8, 0),
                LocalDateTime.of(2026, 1, 2, 8, 0), base.agreedRate(), base.requiresDocuments(),
                base.status(), base.notes(), base.createdAt());
        assertTrue(ServiceRequestFlow.autoSchedule(dated).status() == RequestStatus.SCHEDULED);
    }

    @Test
    void autoScheduleLeavesRequestsWithoutDatesAlone() { // BR-03
        assertTrue(ServiceRequestFlow.autoSchedule(request(RequestStatus.AUTHORIZED)).status()
                == RequestStatus.AUTHORIZED);
    }

    @Test
    void autoScheduleLeavesRequestedAlone() { // BR-03
        ServiceRequest base = request(RequestStatus.REQUESTED);
        ServiceRequest dated = new ServiceRequest(base.id(), base.folio(), base.clientId(),
                base.clientName(), base.routeId(), base.routeLabel(), base.cargoDescription(),
                base.estimatedWeight(), LocalDateTime.of(2026, 1, 1, 8, 0),
                LocalDateTime.of(2026, 1, 2, 8, 0), base.agreedRate(), base.requiresDocuments(),
                base.status(), base.notes(), base.createdAt());
        assertTrue(ServiceRequestFlow.autoSchedule(dated).status() == RequestStatus.REQUESTED);
    }

    @Test
    void cancelsBeforeTransitWithReason() {
        assertTrue(ServiceRequestFlow.cancel(request(RequestStatus.SCHEDULED), "Cliente cancelo").isOk());
        assertTrue(ServiceRequestFlow.cancel(request(RequestStatus.ASSIGNED), "").isErr());
    }

    @Test
    void cannotCancelOnceInTransit() {
        assertTrue(ServiceRequestFlow.cancel(request(RequestStatus.IN_TRANSIT), "tarde").isErr());
    }

    @Test
    void stateMachineTransitions() { // BR-03
        assertTrue(RequestStatus.REQUESTED.canMoveTo(RequestStatus.AUTHORIZED));
        assertTrue(RequestStatus.IN_TRANSIT.canMoveTo(RequestStatus.DELIVERED));
        assertFalse(RequestStatus.REQUESTED.canMoveTo(RequestStatus.IN_TRANSIT));
        assertFalse(RequestStatus.CLOSED.canMoveTo(RequestStatus.CANCELLED));
    }
}
