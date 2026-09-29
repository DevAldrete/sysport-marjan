package mx.marjan.shared;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;

/**
 * Shared colors and fonts for the dashboard. Kept in one place so the modern
 * look stays consistent and can be tuned without hunting through views.
 */
public final class Theme {

    public static final Color BACKGROUND = new Color(0xF3, 0xF5, 0xF8);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xE2, 0xE6, 0xEC);
    public static final Color TEXT = new Color(0x21, 0x2A, 0x38);
    public static final Color MUTED = new Color(0x6B, 0x74, 0x83);

    public static final Color PRIMARY = new Color(0x2F, 0x6F, 0xED);
    public static final Color SUCCESS = new Color(0x1F, 0x9D, 0x55);
    public static final Color WARNING = new Color(0xD9, 0x7A, 0x06);
    public static final Color DANGER = new Color(0xD9, 0x3A, 0x3A);
    public static final Color INFO = new Color(0x0E, 0x74, 0x90);
    public static final Color PURPLE = new Color(0x6D, 0x45, 0xC8);

    private Theme() {}

    private static Font base(float size, int style) {
        Font font = UIManager.getFont("Label.font");
        if (font == null) {
            font = new Font("SansSerif", Font.PLAIN, 12);
        }
        return font.deriveFont(style, size);
    }

    public static Font regular(float size) {
        return base(size, Font.PLAIN);
    }

    public static Font bold(float size) {
        return base(size, Font.BOLD);
    }

    public static Font title() {
        return bold(17f);
    }

    public static Font section() {
        return bold(14f);
    }

    public static Font subtitle() {
        return regular(12f);
    }

    public static Font value() {
        return bold(28f);
    }

    public static Font caption() {
        return bold(11.5f);
    }
}
