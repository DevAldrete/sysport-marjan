package mx.marjan.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import mx.marjan.clients.Client;
import mx.marjan.clients.ClientRules;
import mx.marjan.clients.ClientStatus;
import mx.marjan.clients.ClientType;
import mx.marjan.clients.PaymentTerms;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.shared.Result;
import mx.marjan.trips.ClosingRules;
import mx.marjan.trips.Delivery;
import mx.marjan.trips.DeliveryStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MasterAndClosingRulesTest {

    @Test
    void acceptsValidRfc() { // BR-02
        assertTrue(ClientRules.validate(client("ABC950101XYZ")).isOk());
        assertTrue(ClientRules.validate(client("ABCD950101XYZ")).isOk());
    }

    @Test
    void rejectsInvalidRfc() { // BR-02
        assertTrue(ClientRules.validate(client("XX")).isErr());
        assertTrue(ClientRules.validate(client("")).isErr());
    }

    @Test
    void requiresDeliveryWhenDocumentsRequired() { // BR-13
        assertTrue(ClosingRules.canClose(request(true), Optional.empty()).isErr());
        assertTrue(ClosingRules.canClose(request(true), Optional.of(delivery(DeliveryStatus.PENDING_DOCUMENTS)))
                .isErr());
        assertTrue(ClosingRules.canClose(request(true), Optional.of(delivery(DeliveryStatus.COMPLETE))).isOk());
    }

    @Test
    void allowsCloseWithoutDocuments() {
        assertTrue(ClosingRules.canClose(request(false), Optional.empty()).isOk());
    }

    private Client client(String rfc) {
        return new Client(0, "Cliente", rfc, "", "", "", "", ClientType.OCCASIONAL,
                PaymentTerms.CASH, BigDecimal.ZERO, 0, ClientStatus.ACTIVE);
    }

    private ServiceRequest request(boolean requiresDocuments) {
        return new ServiceRequest(1, "SR-2026-000001", 1, "Cliente", 1, "A -> B", "Carga",
                BigDecimal.TEN, LocalDateTime.now(), LocalDateTime.now().plusDays(1),
                new BigDecimal("1000"), requiresDocuments, RequestStatus.DELIVERED, "", LocalDateTime.now());
    }

    private Delivery delivery(DeliveryStatus status) {
        return new Delivery(1, 1, "SR-2026-000001", LocalDateTime.now(), "Recibio", "EVID-1", status);
    }
}
