package mx.marjan.shared;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A date editor: an ISO ({@code yyyy-MM-dd}) text field plus a button that
 * opens a small month calendar. A blank value means "no date"; optional fields
 * rely on that, required ones are checked by the caller on submit.
 */
public class DateField extends JPanel {

    private final JTextField text = new JTextField(10);
    private final List<Runnable> changeListeners = new ArrayList<>();

    public DateField(LocalDate value) {
        super(new BorderLayout(2, 0));
        setFocusable(true);
        text.setText(Dates.format(value));
        text.setToolTipText("Formato: AAAA-MM-DD");
        text.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                fireChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                fireChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                fireChanged();
            }
        });

        JButton pick = new JButton("\uD83D\uDCC5");
        pick.setFocusable(false);
        pick.setMargin(new java.awt.Insets(0, 4, 0, 4));
        pick.setToolTipText("Elegir en el calendario");
        pick.addActionListener(event -> new CalendarPopup().show(pick, 0, pick.getHeight()));

        add(text, BorderLayout.CENTER);
        add(pick, BorderLayout.EAST);
    }

    public LocalDate date() {
        return Dates.parseDate(text.getText()).orElse(null);
    }

    public void setDate(LocalDate value) {
        text.setText(Dates.format(value));
    }

    public void addChangeListener(Runnable listener) {
        changeListeners.add(listener);
    }

    private void fireChanged() {
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    @Override
    public boolean requestFocusInWindow() {
        boolean focused = text.requestFocusInWindow();
        text.selectAll();
        return focused;
    }

    /** A lightweight month grid; picks a day and writes it back in ISO format. */
    private final class CalendarPopup extends JPopupMenu {

        private YearMonth month = YearMonth.from(date() != null ? date() : LocalDate.now());

        private CalendarPopup() {
            setBorder(BorderFactory.createLineBorder(new java.awt.Color(160, 160, 160)));
            render();
        }

        private void render() {
            removeAll();
            setLayout(new BorderLayout(4, 4));

            JButton previous = navButton("<", () -> {
                month = month.minusMonths(1);
                render();
            });
            JButton next = navButton(">", () -> {
                month = month.plusMonths(1);
                render();
            });
            JLabel title = new JLabel(monthLabel(), SwingConstants.CENTER);
            title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD));

            JPanel header = new JPanel(new BorderLayout());
            header.add(previous, BorderLayout.WEST);
            header.add(title, BorderLayout.CENTER);
            header.add(next, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            JPanel grid = new JPanel(new GridLayout(0, 7, 1, 1));
            for (DayOfWeek day : orderedDays()) {
                JLabel label = new JLabel(day.getDisplayName(TextStyle.NARROW, new Locale("es")),
                        SwingConstants.CENTER);
                label.setForeground(new java.awt.Color(110, 110, 110));
                grid.add(label);
            }
            int leading = month.atDay(1).getDayOfWeek().getValue() - 1;
            for (int i = 0; i < leading; i++) {
                grid.add(new JLabel(""));
            }
            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                grid.add(dayButton(month.atDay(day)));
            }
            add(grid, BorderLayout.CENTER);

            JButton today = new JButton("Hoy");
            today.addActionListener(event -> {
                setDate(LocalDate.now());
                setVisible(false);
            });
            JButton clear = new JButton("Limpiar");
            clear.addActionListener(event -> {
                setDate(null);
                setVisible(false);
            });
            JPanel footer = new JPanel();
            footer.add(today);
            footer.add(clear);
            add(footer, BorderLayout.SOUTH);

            revalidate();
            repaint();
        }

        private JButton navButton(String label, Runnable action) {
            JButton button = new JButton(label);
            button.setFocusable(false);
            button.setMargin(new java.awt.Insets(0, 6, 0, 6));
            button.addActionListener(event -> action.run());
            return button;
        }

        private JButton dayButton(LocalDate day) {
            JButton button = new JButton(String.valueOf(day.getDayOfMonth()));
            button.setFocusable(false);
            button.setMargin(new java.awt.Insets(0, 2, 0, 2));
            button.setPreferredSize(new Dimension(32, 26));
            if (day.equals(date())) {
                button.setBorder(BorderFactory.createLineBorder(new java.awt.Color(60, 110, 190), 2));
            }
            if (day.equals(LocalDate.now())) {
                button.setForeground(new java.awt.Color(190, 60, 60));
            }
            button.addActionListener(event -> {
                setDate(day);
                setVisible(false);
            });
            return button;
        }

        private String monthLabel() {
            String name = month.getMonth().getDisplayName(TextStyle.FULL, new Locale("es"));
            return name.substring(0, 1).toUpperCase(new Locale("es")) + name.substring(1)
                    + " " + month.getYear();
        }

        private List<DayOfWeek> orderedDays() {
            return List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }
    }
}
