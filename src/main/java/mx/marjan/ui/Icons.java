package mx.marjan.ui;

import javafx.scene.Node;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Consistent icon factory backed by the Feather pack. */
public final class Icons {

    private Icons() {}

    public static FontIcon icon(Feather glyph, int size) {
        FontIcon icon = new FontIcon(glyph);
        icon.setIconSize(size);
        return icon;
    }

    public static FontIcon icon(Feather glyph) {
        return icon(glyph, 16);
    }

    /** An icon sized for a button or toolbar action. */
    public static Node action(Feather glyph) {
        FontIcon icon = icon(glyph, 15);
        icon.getStyleClass().add("action-icon");
        return icon;
    }
}
