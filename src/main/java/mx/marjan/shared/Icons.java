package mx.marjan.shared;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * Small, dependency-free vector icons drawn with Java2D. Every glyph is painted
 * on a 0..100 grid and scaled to the requested size, so it stays crisp at any
 * resolution and inherits the color passed in.
 */
public final class Icons {

    private Icons() {}

    @FunctionalInterface
    private interface Painter {
        void paint(Graphics2D g);
    }

    private static Icon render(int size, Color color, Painter painter) {
        return new VectorIcon(size, color == null ? Theme.TEXT : color, painter);
    }

    public static Icon home(int size, Color color) {
        return render(size, color, g -> {
            g.fillRoundRect(13, 13, 33, 33, 8, 8);
            g.fillRoundRect(54, 13, 33, 33, 8, 8);
            g.fillRoundRect(13, 54, 33, 33, 8, 8);
            g.fillRoundRect(54, 54, 33, 33, 8, 8);
        });
    }

    public static Icon request(int size, Color color) {
        return render(size, color, g -> {
            g.drawRoundRect(26, 10, 48, 80, 10, 10);
            g.drawLine(38, 34, 62, 34);
            g.drawLine(38, 48, 62, 48);
            g.drawLine(38, 62, 54, 62);
        });
    }

    public static Icon invoice(int size, Color color) {
        return render(size, color, g -> {
            Path2D receipt = new Path2D.Double();
            receipt.moveTo(28, 10);
            receipt.lineTo(72, 10);
            receipt.lineTo(72, 88);
            receipt.lineTo(65, 80);
            receipt.lineTo(58, 88);
            receipt.lineTo(51, 80);
            receipt.lineTo(44, 88);
            receipt.lineTo(37, 80);
            receipt.lineTo(28, 88);
            receipt.closePath();
            g.draw(receipt);
            g.drawLine(40, 32, 60, 32);
            g.drawLine(40, 46, 60, 46);
        });
    }

    public static Icon license(int size, Color color) {
        return render(size, color, g -> {
            g.drawRoundRect(12, 24, 76, 52, 12, 12);
            g.drawOval(24, 38, 22, 22);
            g.drawLine(58, 42, 76, 42);
            g.drawLine(58, 56, 70, 56);
        });
    }

    public static Icon maintenance(int size, Color color) {
        return render(size, color, g -> {
            g.drawArc(14, 14, 34, 34, 45, 270);
            g.drawLine(42, 42, 80, 80);
            g.drawLine(72, 88, 88, 72);
        });
    }

    public static Icon money(int size, Color color) {
        return render(size, color, g -> {
            g.drawRoundRect(10, 30, 80, 40, 10, 10);
            g.drawOval(42, 40, 16, 16);
            g.drawLine(22, 40, 22, 60);
            g.drawLine(78, 40, 78, 60);
        });
    }

    public static Icon coins(int size, Color color) {
        return render(size, color, g -> {
            g.drawOval(22, 18, 56, 20);
            g.drawOval(22, 40, 56, 20);
            g.drawOval(22, 62, 56, 20);
        });
    }

    public static Icon truck(int size, Color color) {
        return render(size, color, g -> {
            g.drawRect(10, 32, 46, 34);
            Path2D cab = new Path2D.Double();
            cab.moveTo(56, 38);
            cab.lineTo(76, 38);
            cab.lineTo(90, 54);
            cab.lineTo(90, 66);
            cab.lineTo(56, 66);
            cab.closePath();
            g.draw(cab);
            g.drawOval(20, 62, 18, 18);
            g.drawOval(66, 62, 18, 18);
        });
    }

    public static Icon payment(int size, Color color) {
        return render(size, color, g -> {
            g.drawRoundRect(10, 26, 80, 48, 10, 10);
            g.drawLine(10, 42, 90, 42);
            g.drawLine(22, 60, 40, 60);
        });
    }

    public static Icon add(int size, Color color) {
        return render(size, color, g -> {
            g.drawLine(50, 22, 50, 78);
            g.drawLine(22, 50, 78, 50);
        });
    }

    public static Icon refresh(int size, Color color) {
        return render(size, color, g -> {
            g.drawArc(22, 22, 56, 56, 45, 270);
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(82, 36);
            arrow.lineTo(96, 50);
            arrow.lineTo(82, 64);
            arrow.closePath();
            g.fill(arrow);
        });
    }

    public static Icon user(int size, Color color) {
        return render(size, color, g -> {
            g.drawOval(36, 14, 28, 28);
            g.drawArc(20, 50, 60, 52, 0, 180);
        });
    }

    public static Icon route(int size, Color color) {
        return render(size, color, g -> {
            Path2D pin = new Path2D.Double();
            pin.moveTo(50, 90);
            pin.curveTo(20, 58, 18, 20, 50, 12);
            pin.curveTo(82, 20, 80, 58, 50, 90);
            g.draw(pin);
            g.fillOval(42, 32, 16, 16);
        });
    }

    public static Icon chart(int size, Color color) {
        return render(size, color, g -> {
            g.fillRoundRect(18, 58, 16, 30, 4, 4);
            g.fillRoundRect(42, 40, 16, 48, 4, 4);
            g.fillRoundRect(66, 22, 16, 66, 4, 4);
        });
    }

    public static Icon fuel(int size, Color color) {
        return render(size, color, g -> {
            Path2D drop = new Path2D.Double();
            drop.moveTo(50, 10);
            drop.curveTo(82, 52, 78, 88, 50, 88);
            drop.curveTo(22, 88, 18, 52, 50, 10);
            g.draw(drop);
        });
    }

    public static Icon car(int size, Color color) {
        return render(size, color, g -> {
            Path2D body = new Path2D.Double();
            body.moveTo(10, 62);
            body.lineTo(20, 44);
            body.lineTo(72, 44);
            body.lineTo(88, 62);
            body.lineTo(90, 62);
            body.lineTo(90, 72);
            body.lineTo(10, 72);
            body.closePath();
            g.draw(body);
            g.drawLine(28, 46, 40, 58);
            g.drawLine(64, 46, 52, 58);
            g.drawOval(22, 66, 16, 16);
            g.drawOval(62, 66, 16, 16);
        });
    }

    public static Icon alert(int size, Color color) {
        return render(size, color, g -> {
            Path2D triangle = new Path2D.Double();
            triangle.moveTo(50, 12);
            triangle.lineTo(90, 86);
            triangle.lineTo(10, 86);
            triangle.closePath();
            g.draw(triangle);
            g.drawLine(50, 40, 50, 62);
            g.fillOval(46, 70, 8, 8);
        });
    }

    private static final class VectorIcon implements Icon {

        private final int size;
        private final Color color;
        private final Painter painter;

        VectorIcon(int size, Color color, Painter painter) {
            this.size = size;
            this.color = color;
            this.painter = painter;
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.translate(x, y);
                g.scale(size / 100.0, size / 100.0);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        RenderingHints.VALUE_STROKE_PURE);
                g.setColor(color);
                g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                painter.paint(g);
            } finally {
                g.dispose();
            }
        }
    }
}
