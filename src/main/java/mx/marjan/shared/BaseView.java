package mx.marjan.shared;

import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;

/**
 * Common screen shell: a border layout with padding, plus helpers to load data
 * on a background thread and refresh the view. Views only display and collect input.
 */
public abstract class BaseView extends JPanel {

    private final JLabel status = new JLabel(" ");

    protected BaseView() {
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(status, BorderLayout.SOUTH);
    }

    /** Reloads the view's data from the database (implemented by each screen). */
    public abstract void reload();

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess) {
        load(task, onSuccess, failure -> Ui.failure(this, failure));
    }

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        setStatus("Cargando...");
        Async.run(task, value -> {
            setStatus(" ");
            onSuccess.accept(value);
        }, failure -> {
            setStatus(" ");
            onError.accept(failure);
        });
    }

    /** Like {@link #load} but reports an empty result set, for list screens. */
    protected <T> void loadRows(Callable<List<T>> task, Consumer<List<T>> onSuccess) {
        load(task, rows -> {
            setStatus(rows.isEmpty() ? "Sin resultados" : " ");
            onSuccess.accept(rows);
        });
    }

    protected void setStatus(String message) {
        status.setText(message);
    }

    /** The record selected in a view's table, or null when nothing is selected. */
    protected <T> T selectedRow(JTable table, RecordTableModel<T> model) {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }
}
