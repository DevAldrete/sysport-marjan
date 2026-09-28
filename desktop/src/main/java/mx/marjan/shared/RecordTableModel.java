package mx.marjan.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.table.AbstractTableModel;

/** One generic table model for every entity: columns are lambdas over the row type. */
public class RecordTableModel<T> extends AbstractTableModel {

    public record Column<T>(String title, Class<?> type, Function<T, Object> getter) {
        public static <T> Column<T> of(String title, Function<T, Object> getter) {
            return new Column<>(title, Object.class, getter);
        }

        /** A text column collapsed to one line and cut to {@code max} characters. */
        public static <T> Column<T> text(String title, Function<T, String> getter, int max) {
            return of(title, row -> Text.truncate(getter.apply(row), max));
        }
    }

    private final List<Column<T>> columns;
    private List<T> rows = new ArrayList<>();

    public RecordTableModel(List<Column<T>> columns) {
        this.columns = List.copyOf(columns);
    }

    public void setRows(List<T> newRows) {
        this.rows = new ArrayList<>(newRows);
        fireTableDataChanged();
    }

    public T rowAt(int index) {
        return rows.get(index);
    }

    public List<T> rows() {
        return List.copyOf(rows);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columns.size();
    }

    @Override
    public String getColumnName(int column) {
        return columns.get(column).title();
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return columns.get(column).type();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        return columns.get(columnIndex).getter().apply(rows.get(rowIndex));
    }
}
