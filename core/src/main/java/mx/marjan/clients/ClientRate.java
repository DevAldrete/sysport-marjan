package mx.marjan.clients;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientRate(
        long id,
        long clientId,
        long routeId,
        String routeLabel,
        BigDecimal rate,
        LocalDate validFrom,
        LocalDate validTo) {

    public static ClientRate empty() {
        return new ClientRate(0, 0, 0, "", BigDecimal.ZERO, LocalDate.now(), null);
    }
}
