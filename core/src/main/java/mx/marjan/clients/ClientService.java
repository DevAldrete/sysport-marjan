package mx.marjan.clients;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Result;

/** Use cases for clients and negotiated rates. Permission checks live here, not in the UI. */
public class ClientService {

    private final ClientRepository clients = new ClientRepository();
    private final ClientRateRepository rates = new ClientRateRepository();

    public List<Client> search(String term) {
        return clients.search(term);
    }

    public List<Client> listActive() {
        return clients.listActive();
    }

    public Optional<Client> find(long id) {
        return clients.findById(id);
    }

    public Result<Client> save(Client client) {
        if (!Session.has(Permissions.CLIENTS_WRITE)) {
            return Result.err("No tiene permiso para modificar clientes");
        }
        Result<Long> saved = clients.save(client);
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return Result.ok(withId(client, saved.value()));
    }

    /** Hard delete. Fails (surfaced to the UI) when the client has related records. */
    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.CLIENTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar clientes");
        }
        return clients.delete(id);
    }

    public Result<Void> deactivate(long id) {
        return changeStatus(id, ClientStatus.INACTIVE);
    }

    public Result<Void> activate(long id) {
        return changeStatus(id, ClientStatus.ACTIVE);
    }

    private Result<Void> changeStatus(long id, ClientStatus status) {
        if (!Session.has(Permissions.CLIENTS_WRITE)) {
            return Result.err("No tiene permiso para modificar clientes");
        }
        return clients.setStatus(id, status);
    }

    public List<ClientRate> ratesFor(long clientId) {
        return rates.listByClient(clientId);
    }

    public Optional<BigDecimal> suggestRate(long clientId, long routeId, LocalDate date) {
        return rates.suggestRate(clientId, routeId, date);
    }

    public Result<ClientRate> saveRate(ClientRate rate) {
        if (!Session.has(Permissions.RATES_WRITE)) {
            return Result.err("No tiene permiso para modificar tarifas");
        }
        Result<Long> saved = rates.save(rate);
        if (saved.isErr()) {
            return Result.err(saved.problems());
        }
        return Result.ok(new ClientRate(saved.value(), rate.clientId(), rate.routeId(),
                rate.routeLabel(), rate.rate(), rate.validFrom(), rate.validTo()));
    }

    public Result<Void> deleteRate(long id) {
        if (!Session.has(Permissions.RATES_WRITE)) {
            return Result.err("No tiene permiso para eliminar tarifas");
        }
        return rates.delete(id);
    }

    private Client withId(Client client, long id) {
        return new Client(id, client.name(), client.rfc(), client.address(), client.phone(),
                client.email(), client.contactName(), client.clientType(), client.paymentTerms(),
                client.creditLimit(), client.creditDays(), client.status());
    }
}
