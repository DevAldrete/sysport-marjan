package mx.marjan.api.clients;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.security.annotation.Secured;
import io.micronaut.security.authentication.Authentication;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.clients.Client;
import mx.marjan.clients.ClientRate;
import mx.marjan.clients.ClientService;
import mx.marjan.clients.ClientStatus;
import mx.marjan.clients.ClientType;
import mx.marjan.clients.PaymentTerms;
import mx.marjan.security.Permissions;

/** Client and negotiated-rate use cases. Each method is guarded by its permission. */
@Controller("/api/clients")
public class ClientController {

    @Get
    @Secured(Permissions.CLIENTS_READ)
    public List<Client> search(Authentication authentication, @Nullable @QueryValue String term) {
        return service(authentication).search(term);
    }

    @Get("/active")
    @Secured(Permissions.CLIENTS_READ)
    public List<Client> active(Authentication authentication) {
        return service(authentication).listActive();
    }

    @Get("/suggest-rate")
    @Secured(Permissions.RATES_READ)
    public HttpResponse<?> suggestRate(Authentication authentication, @QueryValue long clientId,
            @QueryValue long routeId, @QueryValue @Format("yyyy-MM-dd") LocalDate date) {
        return service(authentication).suggestRate(clientId, routeId, date)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Get("/{id}")
    @Secured(Permissions.CLIENTS_READ)
    public HttpResponse<?> find(Authentication authentication, long id) {
        return service(authentication).find(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post
    @Secured(Permissions.CLIENTS_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body Client client) {
        return Responses.of(service(authentication).save(normalize(client, 0)));
    }

    @Put("/{id}")
    @Secured(Permissions.CLIENTS_WRITE)
    public HttpResponse<?> update(Authentication authentication, long id, @Body Client client) {
        return Responses.of(service(authentication).save(normalize(client, id)));
    }

    @Post("/{id}/status")
    @Secured(Permissions.CLIENTS_WRITE)
    public HttpResponse<?> changeStatus(Authentication authentication, long id,
            @Body StatusUpdate body) {
        ClientService service = service(authentication);
        return Responses.of(body.status() == ClientStatus.ACTIVE
                ? service.activate(id)
                : service.deactivate(id));
    }

    @Delete("/{id}")
    @Secured(Permissions.CLIENTS_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    @Get("/{id}/rates")
    @Secured(Permissions.RATES_READ)
    public List<ClientRate> rates(Authentication authentication, long id) {
        return service(authentication).ratesFor(id);
    }

    @Post("/{id}/rates")
    @Secured(Permissions.RATES_WRITE)
    public HttpResponse<?> saveRate(Authentication authentication, long id, @Body ClientRate rate) {
        ClientRate forClient = new ClientRate(rate.id(), id, rate.routeId(), rate.routeLabel(),
                rate.rate(), rate.validFrom(), rate.validTo());
        return Responses.of(service(authentication).saveRate(forClient));
    }

    @Delete("/rates/{rateId}")
    @Secured(Permissions.RATES_WRITE)
    public HttpResponse<?> deleteRate(Authentication authentication, long rateId) {
        return Responses.of(service(authentication).deleteRate(rateId));
    }

    private ClientService service(Authentication authentication) {
        return new ClientService(Callers.forAuthentication(authentication));
    }

    /** Fills the required fields a partial payload may omit, before the database validates. */
    private Client normalize(Client client, long id) {
        return new Client(
                id,
                client.name(),
                client.rfc(),
                client.address(),
                client.phone(),
                client.email(),
                client.contactName(),
                client.clientType() == null ? ClientType.OCCASIONAL : client.clientType(),
                client.paymentTerms() == null ? PaymentTerms.CASH : client.paymentTerms(),
                client.creditLimit() == null ? BigDecimal.ZERO : client.creditLimit(),
                client.creditDays(),
                client.status() == null ? ClientStatus.ACTIVE : client.status());
    }

    public record StatusUpdate(ClientStatus status) {}
}
