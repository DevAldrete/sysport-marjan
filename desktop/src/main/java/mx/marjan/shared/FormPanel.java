package mx.marjan.shared;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Builds a labelled form with a stable set of fields. Views read values by key.
 *
 * <p>Fields can carry an inline hint, a live validator (the field is outlined in
 * red and the hint explains the problem as the user types) and read-only
 * computed fields that update from the other inputs. Nothing here validates
 * business rules; those remain in the database.</p>
 */
public final class FormPanel {

    private static final Insets INSETS = new Insets(3, 3, 1, 3);
    private static final Color HINT_COLOR = new Color(110, 110, 110);
    private static final Color ERROR_COLOR = new Color(180, 0, 0);
    private static final Color INVALID_BORDER = new Color(205, 70, 70);

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final Map<String, JLabel> hints = new LinkedHashMap<>();
    private final Map<String, String> baseHints = new LinkedHashMap<>();
    private final Map<String, Function<String, String>> validators = new LinkedHashMap<>();
    private final Map<String, Supplier<String>> computed = new LinkedHashMap<>();
    private final Map<String, Border> originalBorders = new LinkedHashMap<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final java.util.Set<String> touched = new java.util.HashSet<>();
    private boolean refreshing;
    private int row;

    public FormPanel addText(String key, String label, String value) {
        return addText(key, label, value, null);
    }

    public FormPanel addText(String key, String label, String value, String hint) {
        JTextField field = new JTextField(value == null ? "" : value, 24);
        watch(key, field);
        put(key, label, field, hint);
        return this;
    }

    public FormPanel addPassword(String key, String label) {
        return addPassword(key, label, null);
    }

    public FormPanel addPassword(String key, String label, String hint) {
        JPasswordField field = new JPasswordField(24);
        watch(key, field);
        put(key, label, field, hint);
        return this;
    }

    public FormPanel addCombo(String key, String label, Object[] items, Object selected) {
        JComboBox<Object> combo = new JComboBox<>(items);
        // Records render through their toString(); truncate so a long value never stretches the dialog.
        combo.setRenderer(new CompactRenderer());
        if (selected != null) {
            combo.setSelectedItem(selected);
        }
        combo.addActionListener(event -> {
            touched.add(key);
            refresh();
        });
        put(key, label, combo, null);
        return this;
    }

    public FormPanel addCheck(String key, String label, boolean value) {
        JCheckBox box = new JCheckBox();
        box.setSelected(value);
        box.addActionListener(event -> {
            touched.add(key);
            refresh();
        });
        put(key, label, box, null);
        return this;
    }

    public FormPanel addArea(String key, String label, String value) {
        return addArea(key, label, value, null);
    }

    public FormPanel addArea(String key, String label, String value, String hint) {
        JTextArea area = new JTextArea(value == null ? "" : value, 3, 24);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        watch(key, area);
        // Store the text area (so text()/setText() can read it) but display a scroll pane.
        put(key, label, area, new JScrollPane(area), hint);
        return this;
    }

    /** A read-only field whose value is recomputed from the other fields on every change. */
    public FormPanel addComputed(String key, String label, Supplier<String> supplier) {
        JTextField field = new JTextField(24);
        field.setEditable(false);
        field.setFocusable(false);
        put(key, label, field, null);
        computed.put(key, supplier);
        return this;
    }

    /** Adds a full-width custom component on its own row (e.g. a child-list editor). */
    public FormPanel addSection(JComponent component) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.gridwidth = 2;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = INSETS;
        panel.add(component, constraints);
        row += 2;
        return this;
    }

    /** A live format check. The message is shown once the user has edited the field. */
    public FormPanel validate(String key, Function<String, String> validator) {
        validators.put(key, validator);
        return this;
    }

    /** Sets the persistent hint shown under a field when it is not reporting an error. */
    public FormPanel hint(String key, String text) {
        baseHints.put(key, text);
        refresh();
        return this;
    }

    /** Runs an action when a combo's selection changes (e.g. to prefill a dependent field). */
    public FormPanel onSelect(String key, Runnable action) {
        JComponent component = fields.get(key);
        if (component instanceof JComboBox<?> combo) {
            combo.addActionListener(event -> {
                if (!refreshing) {
                    action.run();
                }
            });
        }
        return this;
    }

    /** Notified after every change, once computed fields and validators have refreshed. */
    public FormPanel onChange(Runnable listener) {
        changeListeners.add(listener);
        return this;
    }

    /** Recomputes computed fields, re-runs validators and notifies listeners. */
    public void refresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            for (Map.Entry<String, Supplier<String>> entry : computed.entrySet()) {
                JComponent component = fields.get(entry.getKey());
                if (component instanceof JTextField field) {
                    field.setText(entry.getValue().get());
                }
            }
        } finally {
            refreshing = false;
        }
        for (Map.Entry<String, Function<String, String>> entry : validators.entrySet()) {
            String message = entry.getValue().apply(text(entry.getKey()));
            showValidation(entry.getKey(), touched.contains(entry.getKey()) ? message : null);
        }
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    /**
     * Marks every validated field as edited and reports whether all pass, so a
     * dialog can block submit while a field is still red.
     */
    public boolean isValid() {
        touched.addAll(validators.keySet());
        refresh();
        for (Map.Entry<String, Function<String, String>> entry : validators.entrySet()) {
            if (entry.getValue().apply(text(entry.getKey())) != null) {
                return false;
            }
        }
        return true;
    }

    /** Puts the focus on the first field whose live validator is failing. */
    public void focusFirstInvalid() {
        for (Map.Entry<String, Function<String, String>> entry : validators.entrySet()) {
            if (entry.getValue().apply(text(entry.getKey())) != null) {
                JComponent component = fields.get(entry.getKey());
                if (component != null) {
                    component.requestFocusInWindow();
                    if (component instanceof JTextField field) {
                        field.selectAll();
                    }
                }
                return;
            }
        }
    }

    /** Puts the keyboard focus on the first editable field, ready for typing. */
    public void focusFirst() {
        JComponent target = null;
        for (JComponent component : fields.values()) {
            if (component.isEnabled() && component.isFocusable() && component.isVisible()) {
                target = component;
                break;
            }
        }
        if (target == null) {
            return;
        }
        final JComponent focus = target;
        javax.swing.SwingUtilities.invokeLater(() -> {
            focus.requestFocusInWindow();
            if (focus instanceof JTextField field) {
                field.selectAll();
            }
        });
    }

    private void watch(String key, JTextArea area) {
        area.getDocument().addDocumentListener(new SimpleDocumentListener(key));
    }

    private void watch(String key, javax.swing.text.JTextComponent field) {
        field.getDocument().addDocumentListener(new SimpleDocumentListener(key));
    }

    private void put(String key, String label, JComponent field) {
        put(key, label, field, field, null);
    }

    private void put(String key, String label, JComponent field, String hint) {
        put(key, label, field, field, hint);
    }

    private void put(String key, String label, JComponent fieldToStore, JComponent fieldToDisplay, String hint) {
        GridBagConstraints labelConstraints = new GridBagConstraints();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.anchor = GridBagConstraints.NORTHEAST;
        labelConstraints.insets = INSETS;
        panel.add(new JLabel(label), labelConstraints);

        GridBagConstraints fieldConstraints = new GridBagConstraints();
        fieldConstraints.gridx = 1;
        fieldConstraints.gridy = row;
        fieldConstraints.weightx = 1;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        fieldConstraints.insets = INSETS;
        panel.add(fieldToDisplay, fieldConstraints);

        JLabel hintLabel = new JLabel(hint == null ? " " : hint);
        hintLabel.setFont(hintLabel.getFont().deriveFont(11f));
        hintLabel.setForeground(HINT_COLOR);
        hintLabel.setVisible(hint != null);
        GridBagConstraints hintConstraints = new GridBagConstraints();
        hintConstraints.gridx = 1;
        hintConstraints.gridy = row + 1;
        hintConstraints.anchor = GridBagConstraints.WEST;
        hintConstraints.insets = new Insets(0, 3, 3, 3);
        panel.add(hintLabel, hintConstraints);

        fields.put(key, fieldToStore);
        hints.put(key, hintLabel);
        originalBorders.put(key, fieldToStore.getBorder());
        if (hint != null) {
            baseHints.put(key, hint);
            fieldToDisplay.setToolTipText(hint);
        }
        row += 2;
    }

    private void showValidation(String key, String message) {
        JLabel hintLabel = hints.get(key);
        JComponent field = fields.get(key);
        if (hintLabel == null || field == null) {
            return;
        }
        if (message == null) {
            String base = baseHints.get(key);
            hintLabel.setText(base == null ? " " : base);
            hintLabel.setForeground(HINT_COLOR);
            hintLabel.setVisible(base != null);
            field.setBorder(originalBorders.get(key));
        } else {
            hintLabel.setText(message);
            hintLabel.setForeground(ERROR_COLOR);
            hintLabel.setVisible(true);
            Border original = originalBorders.get(key);
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(INVALID_BORDER), original == null ? BorderFactory.createEmptyBorder() : original));
        }
    }

    public String text(String key) {
        JComponent component = fields.get(key);
        if (component instanceof JTextArea area) {
            return area.getText().trim();
        }
        if (component instanceof JPasswordField password) {
            return new String(password.getPassword());
        }
        if (component instanceof JTextField text) {
            return text.getText().trim();
        }
        return "";
    }

    public void setText(String key, String value) {
        JComponent component = fields.get(key);
        String text = value == null ? "" : value;
        if (component instanceof JTextArea area) {
            area.setText(text);
        } else if (component instanceof JPasswordField password) {
            password.setText(text);
        } else if (component instanceof JTextField field) {
            field.setText(text);
        }
    }

    public Object selected(String key) {
        return ((JComboBox<?>) fields.get(key)).getSelectedItem();
    }

    public boolean checked(String key) {
        return ((JCheckBox) fields.get(key)).isSelected();
    }

    public JComponent field(String key) {
        return fields.get(key);
    }

    public JPanel panel() {
        return panel;
    }

    /** Marks a field as edited and refreshes dependents and validators. */
    private final class SimpleDocumentListener implements DocumentListener {
        private final String key;

        private SimpleDocumentListener(String key) {
            this.key = key;
        }

        @Override
        public void insertUpdate(DocumentEvent event) {
            changed();
        }

        @Override
        public void removeUpdate(DocumentEvent event) {
            changed();
        }

        @Override
        public void changedUpdate(DocumentEvent event) {
            changed();
        }

        private void changed() {
            if (refreshing) {
                return;
            }
            touched.add(key);
            refresh();
        }
    }

    /** One-line, fixed-length combo label: keeps long record values from widening the dialog. */
    private static final class CompactRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            return super.getListCellRendererComponent(list, Text.label(value), index, selected, focused);
        }
    }
}
