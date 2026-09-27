package mx.marjan.ui;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

/**
 * Common screen shell: padded border layout with a status bar, plus helpers to
 * load data on a background thread and refresh the view. Views only display and
 * collect input.
 */
public abstract class BaseView extends BorderPane {

    private final Label status = new Label(" ");
    private final ProgressIndicator spinner = new ProgressIndicator();

    protected BaseView() {
        getStyleClass().add("base-view");
        setPadding(new Insets(16));
        spinner.setPrefSize(14, 14);
        spinner.setVisible(false);
        spinner.setManaged(false);
        status.getStyleClass().add("status-text");
        HBox bar = new HBox(8, spinner, status);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 0, 0, 0));
        setBottom(bar);
    }

    /** Reloads the view's data from the database (implemented by each screen). */
    public abstract void reload();

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess) {
        load(task, onSuccess, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        setStatus("Cargando...");
        Async.run(task, value -> {
            clearStatus();
            onSuccess.accept(value);
        }, failure -> {
            clearStatus();
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
        boolean busy = message != null && !message.isBlank() && !message.equals("Sin resultados");
        spinner.setVisible(busy);
        spinner.setManaged(busy);
    }

    private void clearStatus() {
        status.setText(" ");
        spinner.setVisible(false);
        spinner.setManaged(false);
    }
}
