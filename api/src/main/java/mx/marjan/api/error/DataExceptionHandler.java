package mx.marjan.api.error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import mx.marjan.shared.DataException;

/**
 * A JDBC failure already translated to a user-friendly message by core
 * {@code Database.translate}. The cause is logged by the framework; the body
 * stays generic enough for the client to show.
 */
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class DataExceptionHandler implements ExceptionHandler<DataException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, DataException exception) {
        return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("problems", List.of(exception.getMessage())));
    }
}
