package mx.marjan.shared;

import java.util.List;
import java.util.function.Function;

/**
 * The outcome of a business operation: either a value or a list of problems.
 * Rules and services return this instead of throwing for normal flow.
 */
public sealed interface Result<T> permits Result.Ok, Result.Err {

    record Ok<T>(T value) implements Result<T> {}

    record Err<T>(List<String> problems) implements Result<T> {}

    static <T> Result<T> ok(T value) {
        return new Ok<>(value);
    }

    static <T> Result<T> err(String problem) {
        return new Err<>(List.of(problem));
    }

    static <T> Result<T> err(List<String> problems) {
        return new Err<>(List.copyOf(problems));
    }

    default boolean isOk() {
        return this instanceof Ok<T>;
    }

    default boolean isErr() {
        return this instanceof Err<T>;
    }

    default T value() {
        if (this instanceof Ok<T> ok) {
            return ok.value();
        }
        throw new IllegalStateException("Result is an error: " + String.join("; ", problems()));
    }

    default List<String> problems() {
        return this instanceof Err<T> err ? err.problems() : List.of();
    }

    default <R> Result<R> map(Function<T, R> mapper) {
        return this instanceof Ok<T> ok ? ok(mapper.apply(ok.value())) : err(problems());
    }
}
