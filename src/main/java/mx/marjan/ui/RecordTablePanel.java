package mx.marjan.ui;

import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * A table plus selection tracking and an action row, for a parent record's
 * child rows (a trip's expenses, advances, incidents, ...).
 */
public final class RecordTablePanel<T> extends BorderPane {

    private final RecordTable<T> table;

    public RecordTablePanel(RecordTable<T> table) {
        this.table = table;
        setCenter(table);
    }

    public RecordTablePanel<T> withActions(Node... actions) {
        HBox bar = new HBox(8, actions);
        bar.setPadding(new Insets(8, 0, 0, 0));
        setBottom(bar);
        return this;
    }

    public T selected() {
        return table.selected();
    }

    public void setRows(List<T> rows) {
        table.setRows(rows);
    }

    public RecordTable<T> table() {
        return table;
    }

    /** A flexible spacer that pushes following actions to the right of a toolbar. */
    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
