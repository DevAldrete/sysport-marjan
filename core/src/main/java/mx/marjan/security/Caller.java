package mx.marjan.security;

/**
 * Who is performing a use case. Services depend on this instead of the
 * desktop-only static {@link Session}, so the same domain code can run in a
 * multi-user server where the caller is request-scoped.
 */
public interface Caller {

    /** The acting user's id, stored with every audited write (BR-22). */
    long userId();

    /** Whether the acting user holds {@code permission}, e.g. {@code clients.write}. */
    boolean has(String permission);
}
