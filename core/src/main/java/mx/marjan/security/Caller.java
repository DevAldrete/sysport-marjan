package mx.marjan.security;

/**
 * Who is performing a use case. Services depend on this so a multi-user server
 * can supply a request-scoped identity and permissions.
 */
public interface Caller {

    /**
     * A caller for pure identity lookups (login, token rehydration) where
     * permissions are never consulted. Calling {@link #userId()} or
     * {@link #has(String)} on it is a programming error.
     */
    Caller NONE = new Caller() {
        @Override
        public long userId() {
            throw new IllegalStateException("This operation does not act as a user");
        }

        @Override
        public boolean has(String permission) {
            return false;
        }
    };

    /** The acting user's id, stored with every audited write (BR-22). */
    long userId();

    /** Whether the acting user holds {@code permission}, e.g. {@code clients.write}. */
    boolean has(String permission);
}
