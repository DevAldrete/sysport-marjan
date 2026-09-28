package mx.marjan.api.routes;

import io.micronaut.core.annotation.Nullable;
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
import java.util.List;
import mx.marjan.api.http.Responses;
import mx.marjan.api.security.Callers;
import mx.marjan.routes.Route;
import mx.marjan.routes.RouteService;
import mx.marjan.security.Permissions;

/** Route CRUD (FR-RTE-1). */
@Controller("/api/routes")
public class RouteController {

    @Get
    @Secured(Permissions.ROUTES_READ)
    public List<Route> search(Authentication authentication, @Nullable @QueryValue String term) {
        return service(authentication).search(term);
    }

    @Get("/all")
    @Secured(Permissions.ROUTES_READ)
    public List<Route> all(Authentication authentication) {
        return service(authentication).listAll();
    }

    @Get("/{id}")
    @Secured(Permissions.ROUTES_READ)
    public HttpResponse<?> find(Authentication authentication, long id) {
        return service(authentication).find(id)
                .<HttpResponse<?>>map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post
    @Secured(Permissions.ROUTES_WRITE)
    public HttpResponse<?> create(Authentication authentication, @Body Route route) {
        return Responses.of(service(authentication).save(normalize(route, 0)));
    }

    @Put("/{id}")
    @Secured(Permissions.ROUTES_WRITE)
    public HttpResponse<?> update(Authentication authentication, long id, @Body Route route) {
        return Responses.of(service(authentication).save(normalize(route, id)));
    }

    @Delete("/{id}")
    @Secured(Permissions.ROUTES_WRITE)
    public HttpResponse<?> delete(Authentication authentication, long id) {
        return Responses.of(service(authentication).delete(id));
    }

    private RouteService service(Authentication authentication) {
        return new RouteService(Callers.forAuthentication(authentication));
    }

    private Route normalize(Route route, long id) {
        return new Route(id, route.origin(), route.destination(),
                route.estimatedKm() == null ? BigDecimal.ZERO : route.estimatedKm(),
                route.description());
    }
}
