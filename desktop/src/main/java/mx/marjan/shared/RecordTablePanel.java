package mx.marjan.shared;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JTable;

/**
 * A table plus selection tracking for a parent record's child rows (a trip's
 * expenses, advances, incidents, ...). Replaces the array-plus-listener
 * boilerplate that was repeated in each detail panel.
 */
public final class RecordTablePanel<T> extends JPanel {

    private final RecordTableModel<T> model;
    private T selected;

    public RecordTablePanel(RecordTableModel<T> model) {
        super(new BorderLayout(8, 8));
        this.model = model;
        JTable table = Ui.table(model);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            selected = row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
        });
        add(Ui.scroll(table), BorderLayout.CENTER);
    }

    public T selected() {
        return selected;
    }

    public void setRows(List<T> rows) {
        model.setRows(rows);
    }

    public RecordTablePanel<T> withActions(Component... actions) {
        add(Ui.row(actions), BorderLayout.SOUTH);
        return this;
    }
}
