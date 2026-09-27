package mx.marjan.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import atlantafx.base.theme.Theme;
import java.net.URL;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.scene.Scene;

/**
 * Owns the AtlantaFX theme (Primer light/dark) and the app stylesheet. The
 * choice is remembered between sessions.
 */
public final class ThemeManager {

    public enum Mode {
        LIGHT(new PrimerLight(), "Claro"),
        DARK(new PrimerDark(), "Oscuro");

        private final Theme theme;
        private final String label;

        Mode(Theme theme, String label) {
            this.theme = theme;
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
    private static final String APP_CSS = "/mx/marjan/app.css";

    private static Mode mode = Mode.valueOf(PREFS.get("theme", Mode.LIGHT.name()));

    private ThemeManager() {}

    public static Mode mode() {
        return mode;
    }

    public static void setMode(Mode newMode) {
        mode = newMode;
        PREFS.put("theme", mode.name());
    }

    public static Mode toggle() {
        setMode(mode == Mode.LIGHT ? Mode.DARK : Mode.LIGHT);
        return mode;
    }

    /** Applies the current theme and app stylesheet to a scene's root. */
    public static void apply(Scene scene) {
        Application.setUserAgentStylesheet(mode.theme.getUserAgentStylesheet());
        var classes = scene.getRoot().getStyleClass();
        classes.remove("theme-dark");
        if (mode == Mode.DARK) {
            classes.add("theme-dark");
        }
        URL css = ThemeManager.class.getResource(APP_CSS);
        if (css != null) {
            String url = css.toExternalForm();
            if (!scene.getStylesheets().contains(url)) {
                scene.getStylesheets().add(url);
            }
        }
    }
}
