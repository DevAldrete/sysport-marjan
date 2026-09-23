package mx.marjan.security;

import java.util.Set;

/** The logged-in user plus the permissions resolved from their role at login. */
public record CurrentUser(long id, String username, String roleName, Set<String> permissions) {

    public CurrentUser {
        permissions = Set.copyOf(permissions);
    }

    public boolean can(String permission) {
        return permissions.contains(permission);
    }
}
