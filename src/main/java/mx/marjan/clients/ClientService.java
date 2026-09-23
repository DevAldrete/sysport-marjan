package mx.marjan.clients;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
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
        Result<Client> validated = ClientRules.validate(client);
        if (validated.isErr()) {
            return validated;
        }
        if (client.id() == 0) {
            Long id = Database.inTransaction(connection -> clients.insert(connection, client));
            return Result.ok(client.withId(id));
        }
        clients.update(client);
        return Result.ok(client);
    }

    /** Hard delete. Fails (surfaced to the UI) when the client has related records. */
    public Result<Void> delete(long id) {
        if (!Session.has(Permissions.CLIENTS_WRITE)) {
            return Result.err("No tiene permiso para eliminar clientes");
        }
        clients.delete(id);
        return Result.ok(null);
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
        clients.setStatus(id, status);
        return Result.ok(null);
    }

    public List<ClientRate> ratesFor(long clientId) {
        return rates.listByClient(clientId);
    }

    public Optional<BigDecimal> suggestRate(long clientId, long routeId, LocalDate date) {
        return ClientRules.suggestRate(rates.listByClient(clientId), routeId, date);
    }

    public Result<ClientRate> saveRate(ClientRate rate) {
        if (!Session.has(Permissions.RATES_WRITE)) {
            return Result.err("No tiene permiso para modificar tarifas");
        }
        if (rate.routeId() == 0) {
            return Result.err("Debe seleccionar una ruta");
        }
        if (rate.rate() == null || rate.rate().signum() <= 0
                || !mx.marjan.shared.Validators.isMoney(rate.rate())) {
            return Result.err("La tarifa debe ser mayor a cero y dentro del rango permitido");
        }
        if (rate.validFrom() == null) {
            return Result.err("La fecha de vigencia inicial es obligatoria");
        }
        if (rate.validTo() != null && rate.validTo().isBefore(rate.validFrom())) {
            return Result.err("La vigencia final no puede ser anterior a la inicial");
        }
        if (rate.id() == 0) {
            Long id = Database.inTransaction(connection -> rates.insert(connection, rate));
            return Result.ok(new ClientRate(id, rate.clientId(), rate.routeId(), rate.routeLabel(),
                    rate.rate(), rate.validFrom(), rate.validTo()));
        }
        rates.update(rate);
        return Result.ok(rate);
    }

    public Result<Void> deleteRate(long id) {
        if (!Session.has(Permissions.RATES_WRITE)) {
            return Result.err("No tiene permiso para eliminar tarifas");
        }
        rates.delete(id);
        return Result.ok(null);
    }
}
