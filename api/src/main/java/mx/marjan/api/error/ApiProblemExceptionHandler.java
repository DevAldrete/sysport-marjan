package mx.marjan.api.error;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import java.util.Map;

/** Turns a {@link ApiProblemException} into {@code 422 {"problems":[...]}} for the frontend. */
@Produces(MediaType.APPLICATION_JSON)
@Singleton
public class ApiProblemExceptionHandler
        implements ExceptionHandler<ApiProblemException, HttpResponse<?>> {

    @Override
    public HttpResponse<?> handle(HttpRequest request, ApiProblemException exception) {
        return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of("problems", exception.problems()));
    }
}
