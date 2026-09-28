package mx.marjan.api.error;

import java.util.List;

/** A use-case rejection carrying the database's problem messages (maps to HTTP 422). */
public class ApiProblemException extends RuntimeException {

    private final transient List<String> problems;

    public ApiProblemException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
