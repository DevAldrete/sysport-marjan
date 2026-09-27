package mx.marjan.ui;

import java.util.List;
import java.util.concurrent.Callable;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import mx.marjan.shared.Result;

/**
 * Standard form dialog: fields, Guardar/Cancelar, and consistent problem
 * display. The submit handler runs off the FX thread and the dialog only closes
 * on success.
 */
public final class ModalForm {

    private ModalForm() {}

    public static void show(Window owner, String title, FormPanel form, Callable<Result<?>> onSubmit) {
        show(owner, title, form, onSubmit, null);
    }

    public static void show(Window owner, String title, FormPanel form,
            Callable<Result<?>> onSubmit, Runnable afterSave) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(title);
        dialog.setResizable(true);

        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        Label problems = new Label();
        problems.getStyleClass().add("form-error");
        problems.setWrapText(true);
        problems.setVisible(false);
        problems.setManaged(false);

        VBox content = new VBox(6, form.grid(), problems);
        content.setPadding(new Insets(12, 12, 4, 12));
        content.setMinWidth(360);

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(content);
        pane.getButtonTypes().setAll(cancelType, saveType);
        Button save = (Button) pane.lookupButton(saveType);
        save.getStyleClass().add("accent");

        save.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            save.setDisable(true);
            showProblems(problems, null, false);
            Async.run(onSubmit,
                    result -> {
                        if (result.isErr()) {
                            save.setDisable(false);
                            showProblems(problems, result.problems(), true);
                        } else {
                            dialog.setResult(saveType);
                            dialog.close();
                            if (afterSave != null) {
                                afterSave.run();
                            }
                        }
                    },
                    failure -> {
                        save.setDisable(false);
                        Ui.failure(owner, failure);
                    });
        });

        dialog.setOnShown(event -> form.focusFirst());
        dialog.showAndWait();
    }

    private static void showProblems(Label label, List<String> messages, boolean visible) {
        if (!visible || messages == null || messages.isEmpty()) {
            label.setText("");
            label.setVisible(false);
            label.setManaged(false);
            return;
        }
        label.setText(messages.size() == 1
                ? messages.get(0)
                : "\u2022 " + String.join("\n\u2022 ", messages));
        label.setVisible(true);
        label.setManaged(true);
    }
}
