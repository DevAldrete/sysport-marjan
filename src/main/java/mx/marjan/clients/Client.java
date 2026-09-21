package mx.marjan.clients;

import java.math.BigDecimal;

public record Client(
        long id,
        String name,
        String rfc,
        String address,
        String phone,
        String email,
        String contactName,
        ClientType clientType,
        PaymentTerms paymentTerms,
        BigDecimal creditLimit,
        int creditDays,
        ClientStatus status) {

    public static Client empty() {
        return new Client(0, "", "", "", "", "", "",
                ClientType.OCCASIONAL, PaymentTerms.CASH, BigDecimal.ZERO, 0, ClientStatus.ACTIVE);
    }

    public Client withId(long newId) {
        return new Client(newId, name, rfc, address, phone, email, contactName,
                clientType, paymentTerms, creditLimit, creditDays, status);
    }

    @Override
    public String toString() {
        return name;
    }
}
