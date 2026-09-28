package mx.marjan.security;

/** Adapter that backs {@link Caller} with the desktop's static {@link Session}. */
public final class SessionCaller implements Caller {

    public static final SessionCaller INSTANCE = new SessionCaller();

    private SessionCaller() {}

    @Override
    public long userId() {
        return Session.userId();
    }

    @Override
    public boolean has(String permission) {
        return Session.has(permission);
    }
}
