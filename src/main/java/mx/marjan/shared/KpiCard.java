package mx.marjan.shared;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A clickable summary card: a colored icon badge, a large value and a caption.
 * Cards without an action stay inert.
 */
public final class KpiCard extends Cards.RoundedPanel {

    private final JLabel value = new JLabel("...");
    private final JLabel caption = new JLabel();
    private final Color accent;
    private final Color base = Theme.CARD;

    private Runnable onClick;
    private boolean hover;

    public KpiCard(String caption, Icon icon, Color accent) {
        super(new BorderLayout(12, 0));
        this.accent = accent;
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));

        value.setFont(Theme.value());
        value.setForeground(Theme.TEXT);
        value.setAlignmentX(LEFT_ALIGNMENT);

        this.caption.setText(caption);
        this.caption.setFont(Theme.caption());
        this.caption.setForeground(Theme.MUTED);
        this.caption.setAlignmentX(LEFT_ALIGNMENT);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(Box.createVerticalGlue());
        text.add(value);
        text.add(Box.createVerticalStrut(2));
        text.add(this.caption);
        text.add(Box.createVerticalGlue());

        add(new IconBadge(icon, accent), BorderLayout.WEST);
        add(text, BorderLayout.CENTER);

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hover = true;
                setCursor(onClick == null ? Cursor.getDefaultCursor()
                        : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hover = false;
                repaint();
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (onClick != null) {
                    onClick.run();
                }
            }
        };
        addMouseListener(mouse);
        setPreferredSize(new Dimension(210, 84));
    }

    /** Makes the card clickable (typically to open the relevant tab). */
    public KpiCard onClick(Runnable action) {
        this.onClick = action;
        return this;
    }

    public KpiCard tooltip(String text) {
        setToolTipText(text);
        return this;
    }

    public void setValue(String text) {
        value.setText(text);
    }

    public void setCaption(String text) {
        caption.setText(text);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        setFill(hover && onClick != null ? tint(base, accent, 0.06f) : base);
        setLine(hover && onClick != null ? tint(Theme.BORDER, accent, 0.5f) : Theme.BORDER);
        super.paintComponent(graphics);
    }

    private static Color tint(Color base, Color accent, float amount) {
        float red = base.getRed() + (accent.getRed() - base.getRed()) * amount;
        float green = base.getGreen() + (accent.getGreen() - base.getGreen()) * amount;
        float blue = base.getBlue() + (accent.getBlue() - base.getBlue()) * amount;
        return new Color(Math.round(red), Math.round(green), Math.round(blue));
    }

    /** A soft colored circle with the glyph centered inside. */
    private static final class IconBadge extends JPanel {

        private static final int SIZE = 46;

        private final Icon icon;
        private final Color color;

        IconBadge(Icon icon, Color color) {
            this.icon = icon;
            this.color = color;
            setOpaque(false);
            setPreferredSize(new Dimension(SIZE, SIZE));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 34));
                g.fillOval(0, 0, SIZE, SIZE);
                int x = (SIZE - icon.getIconWidth()) / 2;
                int y = (SIZE - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g, x, y);
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
