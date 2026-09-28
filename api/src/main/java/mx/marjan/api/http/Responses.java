package mx.marjan.api.http;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import java.util.Map;
import mx.marjan.shared.Result;

/** Maps a core {@link Result} to an HTTP response: value on success, problems on failure. */
public final class Responses {

    private Responses() {}

    public static <T> HttpResponse<?> of(Result<T> result) {
        if (result.isErr()) {
            return HttpResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(Map.of("problems", result.problems()));
        }
        T value = result.value();
        return value == null ? HttpResponse.noContent() : HttpResponse.ok(value);
    }
}
