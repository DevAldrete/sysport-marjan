package mx.marjan.shared;

import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import java.util.concurrent.Callable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableCellRenderer;

/** Small, consistent UI helpers. Validation problems and errors surface here only. */
public final class Ui {

    private Ui() {}

    public static void error(Component parent, String title, List<String> problems) {
        JOptionPane.showMessageDialog(parent, String.join("\n", problems), title, JOptionPane.ERROR_MESSAGE);
    }

    public static void error(Component parent, String message) {
        error(parent, "Error", List.of(message));
    }

    public static void info(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Informacion", JOptionPane.INFORMATION_MESSAGE);
    }

    public static boolean confirm(Component parent, String message) {
        return JOptionPane.showConfirmDialog(parent, message, "Confirmar", JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION;
    }

    public static void failure(Component parent, Throwable failure) {
        String message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        error(parent, "Error inesperado", List.of(message));
    }

    /** Confirms a hard delete, runs it off the EDT, and reports success or problems uniformly. */
    public static void delete(Component parent, String what, Callable<Result<Void>> action, Runnable onDone) {
        if (!confirm(parent, "Eliminar definitivamente " + what + "?")) {
            return;
        }
        Async.run(action, result -> {
            if (result.isErr()) {
                error(parent, "No se puede eliminar", result.problems());
            } else if (onDone != null) {
                onDone.run();
            }
        }, failure -> failure(parent, failure));
    }

    public static JButton button(String text, Runnable action) {
        JButton button = new JButton(text);
        button.addActionListener(event -> action.run());
        return button;
    }

    public static JPanel row(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    /** Stacks several rows vertically so a long toolbar never clips its buttons. */
    public static JPanel column(JPanel... rows) {
        JPanel panel = new JPanel(new java.awt.GridLayout(rows.length, 1));
        for (JPanel row : rows) {
            panel.add(row);
        }
        return panel;
    }

    public static JPanel titled(String title, Component content) {
        JPanel panel = new JPanel(new java.awt.BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(content);
        return panel;
    }

    public static JTextField text(int columns) {
        return new JTextField(columns);
    }

    public static JLabel bold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(java.awt.Font.BOLD));
        return label;
    }

    public static JScrollPane scroll(Component content) {
        return new JScrollPane(content);
    }

    public static JTable table(RecordTableModel<?> model) {
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(DefaultTableCellRenderer.LEFT);
        table.setDefaultRenderer(Object.class, renderer);
        return table;
    }
}
