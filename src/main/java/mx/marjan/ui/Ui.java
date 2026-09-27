package mx.marjan.ui;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TableView;
import javafx.stage.Window;
import mx.marjan.shared.DataException;
import mx.marjan.shared.Result;

/** Small, consistent UI helpers. Validation problems and errors surface here only. */
public final class Ui {

    private Ui() {}

    public static Window windowOf(Node node) {
        return node == null || node.getScene() == null ? null : node.getScene().getWindow();
    }

    public static void error(Window owner, String title, List<String> problems) {
        String text = problems.size() <= 1
                ? String.join("\n", problems)
                : "\u2022 " + String.join("\n\u2022 ", problems);
        Alert alert = alert(owner, Alert.AlertType.ERROR, title, text);
        alert.showAndWait();
    }

    public static void error(Window owner, String message) {
        error(owner, "Error", List.of(message));
    }

    public static void info(Window owner, String message) {
        alert(owner, Alert.AlertType.INFORMATION, "Informacion", message).showAndWait();
    }

    public static void success(Window owner, String message) {
        alert(owner, Alert.AlertType.INFORMATION, "Listo", message).showAndWait();
    }

    public static boolean confirm(Window owner, String message) {
        Alert alert = alert(owner, Alert.AlertType.CONFIRMATION, "Confirmar", message);
        alert.getButtonTypes().setAll(new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType("Si", ButtonBar.ButtonData.OK_DONE));
        return alert.showAndWait().map(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .orElse(false);
    }

    /** A confirmation for irreversible actions; the affirmative button reads the given verb. */
    public static boolean confirmDanger(Window owner, String message, String verb) {
        Alert alert = alert(owner, Alert.AlertType.CONFIRMATION, "Confirmar", message);
        alert.getButtonTypes().setAll(new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE),
                new ButtonType(verb, ButtonBar.ButtonData.OK_DONE));
        return alert.showAndWait().map(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .orElse(false);
    }

    public static void failure(Window owner, Throwable failure) {
        String message = failure.getMessage() == null ? failure.toString() : failure.getMessage();
        String title = failure instanceof DataException
                ? "No se pudo completar la operacion"
                : "Error inesperado";
        error(owner, title, List.of(message));
    }

    /** Confirms a hard delete, runs it off the FX thread, and reports success or problems uniformly. */
    public static void delete(Window owner, String what, Callable<Result<Void>> action, Runnable onDone) {
        if (!confirmDanger(owner, "\u00bfEliminar definitivamente " + what + "?", "Eliminar")) {
            return;
        }
        Async.run(action, result -> {
            if (result.isErr()) {
                error(owner, "No se puede eliminar", result.problems());
            } else if (onDone != null) {
                onDone.run();
            }
        }, failure -> failure(owner, failure));
    }

    public static Button button(String text, Runnable action) {
        Button button = new Button(text);
        button.setOnAction(event -> action.run());
        return button;
    }

    public static Button button(String text, String tooltip, Runnable action) {
        Button button = button(text, action);
        button.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        return button;
    }

    public static Button primary(String text, Runnable action) {
        Button button = button(text, action);
        button.setDefaultButton(true);
        button.getStyleClass().add("accent");
        return button;
    }

    /** Calls the action with the selected row when a table row is double-clicked. */
    public static <T> void onDoubleClick(TableView<T> table, Consumer<T> action) {
        table.setRowFactory(view -> {
            javafx.scene.control.TableRow<T> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    action.accept(row.getItem());
                }
            });
            return row;
        });
    }

    private static Alert alert(Window owner, Alert.AlertType type, String title, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setTitle("SysPort MARJAN");
        alert.setHeaderText(title);
        alert.initOwner(owner);
        return alert;
    }
}
