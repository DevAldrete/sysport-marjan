package mx.marjan.clients;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import mx.marjan.shared.Result;

/** Pure validation and selection rules for clients and their rates. */
public final class ClientRules {

    private static final Pattern RFC = Pattern.compile("^[A-ZÑ&]{3,4}\\d{6}[A-Z0-9]{3}$");

    private ClientRules() {}

    /** BR-02: RFC is required and must have a valid format. */
    public static Result<Client> validate(Client client) {
        List<String> problems = new ArrayList<>();
        if (client.name() == null || client.name().isBlank()) {
            problems.add("El nombre o razon social es obligatorio");
        }
        if (client.rfc() == null || client.rfc().isBlank()) {
            problems.add("El RFC es obligatorio");
        } else if (!RFC.matcher(client.rfc().trim().toUpperCase()).matches()) {
            problems.add("El RFC no tiene un formato valido (ej. ABC950101XYZ)");
        }
        if (client.paymentTerms() == PaymentTerms.CREDIT) {
            if (client.creditDays() < 0) {
                problems.add("Los dias de credito no pueden ser negativos");
            }
            if (client.creditLimit() != null && client.creditLimit().signum() < 0) {
                problems.add("El limite de credito no puede ser negativo");
            }
        }
        return problems.isEmpty() ? Result.ok(client) : Result.err(problems);
    }

    /** BR-04: suggests the newest rate valid for the route on the given date. */
    public static Optional<BigDecimal> suggestRate(List<ClientRate> rates, long routeId, LocalDate date) {
        return rates.stream()
                .filter(rate -> rate.routeId() == routeId)
                .filter(rate -> !rate.validFrom().isAfter(date))
                .filter(rate -> rate.validTo() == null || !rate.validTo().isBefore(date))
                .max(Comparator.comparing(ClientRate::validFrom))
                .map(ClientRate::rate);
    }
}
