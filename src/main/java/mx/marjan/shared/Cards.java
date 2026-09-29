package mx.marjan.shared;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Rounded "card" containers used by the dashboard to group widgets. */
public final class Cards {

    private Cards() {}

    /** A titled card: an icon + heading on top of the supplied body. */
    public static JPanel section(String title, Icon icon, JComponent body) {
        RoundedPanel panel = new RoundedPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        JLabel heading = new JLabel(title, icon, JLabel.LEFT);
        heading.setIconTextGap(8);
        heading.setFont(Theme.section());
        heading.setForeground(Theme.TEXT);
        panel.add(heading, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** A white panel with rounded corners and a hairline border. */
    public static class RoundedPanel extends JPanel {

        private int arc = 14;
        private Color fill = Theme.CARD;
        private Color line = Theme.BORDER;

        public RoundedPanel() {
            this(new BorderLayout());
        }

        public RoundedPanel(LayoutManager layout) {
            super(layout);
            setOpaque(false);
        }

        public RoundedPanel arc(int arc) {
            this.arc = arc;
            return this;
        }

        protected void setFill(Color fill) {
            this.fill = fill;
        }

        protected void setLine(Color line) {
            this.line = line;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                if (line != null) {
                    g.setColor(line);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
