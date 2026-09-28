package mx.marjan.api.security;

import mx.marjan.security.Caller;

/** A trusted background identity for scheduled jobs (all permissions, fixed user). */
public final class SystemCaller implements Caller {

    private final long userId;

    public SystemCaller(long userId) {
        this.userId = userId;
    }

    @Override
    public long userId() {
        return userId;
    }

    @Override
    public boolean has(String permission) {
        return true;
    }
}
