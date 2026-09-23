package mx.marjan.shared;

/** A database failure already translated into a message suitable for the UI. */
public class DataException extends RuntimeException {

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}
