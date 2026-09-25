package mx.marjan.shared;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
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

/** Builds a labelled form with a stable set of fields. Views read values by key. */
public final class FormPanel {

    private static final Insets INSETS = new Insets(3, 3, 3, 3);

    private final JPanel panel = new JPanel(new GridBagLayout());
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private int row;

    public FormPanel addText(String key, String label, String value) {
        JTextField field = new JTextField(value == null ? "" : value, 24);
        put(key, label, field);
        return this;
    }

    public FormPanel addPassword(String key, String label) {
        put(key, label, new JPasswordField(24));
        return this;
    }

    public FormPanel addCombo(String key, String label, Object[] items, Object selected) {
        JComboBox<Object> combo = new JComboBox<>(items);
        // Records render through their toString(); truncate so a long value never stretches the dialog.
        combo.setRenderer(new CompactRenderer());
        if (selected != null) {
            combo.setSelectedItem(selected);
        }
        put(key, label, combo);
        return this;
    }

    public FormPanel addCheck(String key, String label, boolean value) {
        JCheckBox box = new JCheckBox();
        box.setSelected(value);
        put(key, label, box);
        return this;
    }

    public FormPanel addArea(String key, String label, String value) {
        JTextArea area = new JTextArea(value == null ? "" : value, 3, 24);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        // Store the text area (so text()/setText() can read it) but display a scroll pane.
        put(key, label, area, new JScrollPane(area));
        return this;
    }

    private void put(String key, String label, JComponent field) {
        put(key, label, field, field);
    }

    private void put(String key, String label, JComponent fieldToStore, JComponent fieldToDisplay) {
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

        fields.put(key, fieldToStore);
        row++;
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

    /** One-line, fixed-length combo label: keeps long record values from widening the dialog. */
    private static final class CompactRenderer extends DefaultListCellRenderer {
        @Override
        public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                int index, boolean selected, boolean focused) {
            return super.getListCellRendererComponent(list, Text.label(value), index, selected, focused);
        }
    }
}
