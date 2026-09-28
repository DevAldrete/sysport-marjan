package mx.marjan.shared;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

/**
 * A date plus time editor: a {@link DateField} and two spinners for hour and
 * minute. A blank date means "no date-time"; the time defaults to 00:00.
 */
public class DateTimeField extends JPanel {

    private final DateField date = new DateField(null);
    private final JSpinner hour = new JSpinner(new SpinnerNumberModel(0, 0, 23, 1));
    private final JSpinner minute = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
    private final List<Runnable> changeListeners = new ArrayList<>();

    public DateTimeField(LocalDateTime value) {
        super(new BorderLayout(4, 0));
        setFocusable(true);
        hour.setEditor(new JSpinner.NumberEditor(hour, "00"));
        minute.setEditor(new JSpinner.NumberEditor(minute, "00"));
        setValue(value);

        JPanel time = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        time.add(hour);
        time.add(new JLabel(":"));
        time.add(minute);
        time.add(new JLabel("hrs"));

        date.addChangeListener(this::fireChanged);
        hour.addChangeListener(event -> fireChanged());
        minute.addChangeListener(event -> fireChanged());

        add(date, BorderLayout.CENTER);
        add(time, BorderLayout.EAST);
    }

    public LocalDateTime dateTime() {
        LocalDateTime value = date.date() == null ? null
                : date.date().atTime((Integer) hour.getValue(), (Integer) minute.getValue());
        return value;
    }

    public void setValue(LocalDateTime value) {
        if (value == null) {
            date.setDate(null);
            hour.setValue(0);
            minute.setValue(0);
            return;
        }
        date.setDate(value.toLocalDate());
        hour.setValue(value.getHour());
        minute.setValue(value.getMinute());
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
        return date.requestFocusInWindow();
    }
}
