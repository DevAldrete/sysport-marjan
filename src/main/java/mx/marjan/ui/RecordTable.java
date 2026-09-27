package mx.marjan.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import mx.marjan.shared.Money;
import mx.marjan.shared.Text;

/**
 * The one data table used by every screen. Columns are declared as lambdas so a
 * screen never has to build {@code TableColumn}s by hand.
 */
public class RecordTable<T> extends TableView<T> {

    public record Column<T>(
            String title,
            Function<T, Object> getter,
            Pos align,
            double width,
            Function<T, Node> renderer) {

        public static <T> Column<T> of(String title, Function<T, Object> getter) {
            return new Column<>(title, getter, Pos.CENTER_LEFT, 140, null);
        }

        public static <T> Column<T> text(String title, Function<T, String> getter, int max) {
            return new Column<>(title, row -> Text.truncate(getter.apply(row), max),
                    Pos.CENTER_LEFT, 200, null);
        }

        public static <T> Column<T> money(String title, Function<T, Object> getter) {
            return new Column<>(title, getter, Pos.CENTER_RIGHT, 120, row -> {
                java.math.BigDecimal value = toBigDecimal(getter.apply(row));
                return new Label(value == null ? "" : Money.format(value));
            });
        }

        public static <T> Column<T> number(String title, Function<T, Object> getter) {
            return new Column<>(title, getter, Pos.CENTER_RIGHT, 100, null);
        }

        public static <T> Column<T> badge(String title, Function<T, String> text,
                Function<T, StatusBadge.Tone> tone) {
            return new Column<>(title, row -> text.apply(row), Pos.CENTER_LEFT, 120,
                    row -> StatusBadge.of(text.apply(row), tone.apply(row)));
        }

        public static <T> Column<T> styled(String title, Function<T, String> text,
                Function<T, String> styleClass) {
            return new Column<>(title, row -> text.apply(row), Pos.CENTER_LEFT, 140, row -> {
                Label label = new Label(text.apply(row));
                label.getStyleClass().add(styleClass.apply(row));
                return label;
            });
        }

        private static java.math.BigDecimal toBigDecimal(Object value) {
            return value instanceof java.math.BigDecimal decimal ? decimal : null;
        }
    }

    public RecordTable(List<Column<T>> columns) {
        getStyleClass().add("data-table");
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        setPlaceholder(new Label("Sin resultados"));
        List<TableColumn<T, Object>> built = new ArrayList<>();
        for (Column<T> column : columns) {
            built.add(build(column));
        }
        getColumns().setAll(built);
    }

    private TableColumn<T, Object> build(Column<T> column) {
        TableColumn<T, Object> built = new TableColumn<>(column.title());
        built.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(column.getter().apply(cell.getValue())));
        built.setPrefWidth(column.width());
        built.setMinWidth(Math.min(60, column.width()));
        built.setStyle("-fx-alignment: " + cssAlignment(column.align()) + ";");
        if (column.renderer() != null) {
            built.setCellFactory(view -> rendererCell(column));
        }
        built.setSortable(true);
        return built;
    }

    private TableCell<T, Object> rendererCell(Column<T> column) {
        return new TableCell<>() {
            @Override
            protected void updateItem(Object item, boolean empty) {
                super.updateItem(item, empty);
                T row = getTableRow() == null ? null : getTableRow().getItem();
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(null);
                setGraphic(column.renderer().apply(row));
            }
        };
    }

    private static String cssAlignment(Pos align) {
        return align == Pos.CENTER_RIGHT ? "center-right" : "center-left";
    }

    public void setRows(List<T> rows) {
        getItems().setAll(rows);
    }

    public T selected() {
        return getSelectionModel().getSelectedItem();
    }
}
