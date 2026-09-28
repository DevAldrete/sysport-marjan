package mx.marjan.security;

/** Holds the current session. Set at login, cleared at logout. */
public final class Session {

    private static CurrentUser currentUser;

    private Session() {}

    public static void login(CurrentUser user) {
        currentUser = user;
    }

    public static void logout() {
        currentUser = null;
    }

    public static CurrentUser user() {
        if (currentUser == null) {
            throw new IllegalStateException("No user logged in");
        }
        return currentUser;
    }

    public static boolean isLoggedIn() {
        return currentUser != null;
    }

    public static boolean has(String permission) {
        return currentUser != null && currentUser.can(permission);
    }

    public static long userId() {
        return user().id();
    }
}
