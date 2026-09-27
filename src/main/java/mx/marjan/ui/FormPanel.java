package mx.marjan.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import mx.marjan.shared.FormModel;
import mx.marjan.shared.Text;

/**
 * Renders a {@link FormModel} into labeled JavaFX inputs. Fields carry inline
 * hints and live validation; computed fields update from the other inputs.
 * Nothing here validates business rules; those remain in the database.
 */
public final class FormPanel {

    private final FormModel model;
    private final GridPane grid = new GridPane();
    private final Map<String, Control> controls = new LinkedHashMap<>();
    private final Map<String, TextField> computedFields = new LinkedHashMap<>();
    private final Map<String, Label> hintLabels = new LinkedHashMap<>();
    private final List<Runnable> changeListeners = new ArrayList<>();
    private final Map<String, Runnable> selectActions = new LinkedHashMap<>();
    private boolean refreshing;
    private int row;

    public FormPanel() {
        this(new FormModel());
    }

    public FormPanel(FormModel model) {
        this.model = model;
        grid.getStyleClass().add("form-grid");
        grid.setHgap(12);
        grid.setVgap(4);
        grid.setPadding(new Insets(4, 4, 4, 4));
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(RegionSize.label());
        labels.setHalignment(Pos.CENTER_RIGHT.getHpos());
        ColumnConstraints fields = new ColumnConstraints();
        fields.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, fields);
    }

    public FormPanel addText(String key, String label, String value) {
        return addText(key, label, value, null);
    }

    public FormPanel addText(String key, String label, String value, String hint) {
        model.addText(key, label, value, hint);
        TextField field = new TextField(value == null ? "" : value);
        field.setPrefColumnCount(24);
        wireText(key, field);
        place(key, field, hint);
        return this;
    }

    public FormPanel addPassword(String key, String label) {
        return addPassword(key, label, null);
    }

    public FormPanel addPassword(String key, String label, String hint) {
        model.addPassword(key, label, hint);
        PasswordField field = new PasswordField();
        field.setPrefColumnCount(24);
        wireText(key, field);
        place(key, field, hint);
        return this;
    }

    public FormPanel addCombo(String key, String label, Object[] items, Object selected) {
        model.addCombo(key, label, items, selected);
        ComboBox<Object> combo = new ComboBox<>();
        combo.getItems().setAll(items);
        combo.setMaxWidth(Double.MAX_VALUE);
        if (selected != null) {
            combo.setValue(selected);
        }
        combo.setCellFactory(view -> new CompactCell());
        combo.setButtonCell(new CompactCell());
        combo.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (refreshing) {
                return;
            }
            model.setSelected(key, newValue);
            userChanged(key);
            Runnable action = selectActions.get(key);
            if (action != null) {
                action.run();
            }
        });
        place(key, combo, null);
        return this;
    }

    public FormPanel addCheck(String key, String label, boolean value) {
        model.addCheck(key, label, value);
        CheckBox box = new CheckBox();
        box.setSelected(value);
        box.selectedProperty().addListener((observable, oldValue, newValue) -> {
            if (refreshing) {
                return;
            }
            model.setChecked(key, newValue);
            userChanged(key);
        });
        place(key, box, null);
        return this;
    }

    public FormPanel addArea(String key, String label, String value) {
        return addArea(key, label, value, null);
    }

    public FormPanel addArea(String key, String label, String value, String hint) {
        model.addArea(key, label, value, hint);
        TextArea area = new TextArea(value == null ? "" : value);
        area.setPrefRowCount(3);
        area.setWrapText(true);
        wireText(key, area);
        place(key, area, hint);
        return this;
    }

    /** A read-only field whose value is recomputed from the others on every change. */
    public FormPanel addComputed(String key, String label, java.util.function.Supplier<String> supplier) {
        model.addComputed(key, label, supplier);
        TextField field = new TextField();
        field.setEditable(false);
        field.setFocusTraversable(false);
        computedFields.put(key, field);
        place(key, field, null);
        return this;
    }

    /** A live format check. The message is shown once the user has edited the field. */
    public FormPanel validate(String key, java.util.function.Function<String, String> validator) {
        model.validate(key, validator);
        return this;
    }

    /** Sets the persistent hint shown under a field when it is not reporting an error. */
    public FormPanel hint(String key, String text) {
        model.hint(key, text);
        refresh();
        return this;
    }

    /** Runs an action when a combo's selection changes (e.g. to prefill a dependent field). */
    public FormPanel onSelect(String key, Runnable action) {
        selectActions.put(key, action);
        return this;
    }

    /** Notified after every change, once computed fields and validators have refreshed. */
    public FormPanel onChange(Runnable listener) {
        changeListeners.add(listener);
        return this;
    }

    /** Recomputes computed fields, re-runs validators and notifies listeners. */
    public void refresh() {
        if (refreshing) {
            return;
        }
        refreshing = true;
        try {
            model.refresh();
            for (Map.Entry<String, TextField> entry : computedFields.entrySet()) {
                entry.getValue().setText(model.text(entry.getKey()));
            }
        } finally {
            refreshing = false;
        }
        Map<String, String> problems = model.validate();
        for (String key : controls.keySet()) {
            showValidation(key, problems.get(key));
        }
        for (Runnable listener : changeListeners) {
            listener.run();
        }
    }

    /** Puts the keyboard focus on the first editable field, ready for typing. */
    public void focusFirst() {
        for (Control control : controls.values()) {
            if (control.isDisabled() || !control.isFocusTraversable()) {
                continue;
            }
            final Control focus = control;
            Platform.runLater(() -> {
                focus.requestFocus();
                if (focus instanceof TextInputControl textInput) {
                    textInput.selectAll();
                }
            });
            return;
        }
    }

    private void userChanged(String key) {
        model.markTouched(key);
        refresh();
    }

    private void wireText(String key, TextInputControl field) {
        field.textProperty().addListener((observable, oldValue, newValue) -> {
            if (refreshing) {
                return;
            }
            model.setText(key, newValue);
            userChanged(key);
        });
    }

    private void place(String key, Control control, String hint) {
        control.getStyleClass().add("form-control");
        controls.put(key, control);
        grid.add(new Label(model.label(key)), 0, row);
        GridPane.setValignment(control, javafx.geometry.VPos.CENTER);
        grid.add(control, 1, row);

        Label hintLabel = new Label(hint == null ? " " : hint);
        hintLabel.getStyleClass().add("form-hint");
        hintLabel.setWrapText(true);
        hintLabel.setVisible(hint != null);
        hintLabel.setManaged(hint != null);
        hintLabels.put(key, hintLabel);
        grid.add(hintLabel, 1, row + 1);
        row += 2;
    }

    private void showValidation(String key, String message) {
        Control control = controls.get(key);
        Label hintLabel = hintLabels.get(key);
        if (control == null || hintLabel == null) {
            return;
        }
        if (message == null) {
            String base = model.baseHint(key);
            hintLabel.setText(base == null ? " " : base);
            hintLabel.setVisible(base != null);
            hintLabel.setManaged(base != null);
            control.getStyleClass().remove("invalid");
        } else {
            hintLabel.setText(message);
            hintLabel.setVisible(true);
            hintLabel.setManaged(true);
            if (!control.getStyleClass().contains("invalid")) {
                control.getStyleClass().add("invalid");
            }
        }
    }

    public String text(String key) {
        return model.text(key);
    }

    public void setText(String key, String value) {
        model.setText(key, value);
        Control control = controls.get(key);
        if (control instanceof TextInputControl textInput) {
            if (!textInput.getText().equals(value == null ? "" : value)) {
                textInput.setText(value == null ? "" : value);
            }
        }
        refresh();
    }

    public Object selected(String key) {
        return model.selected(key);
    }

    public boolean checked(String key) {
        return model.checked(key);
    }

    public Control control(String key) {
        return controls.get(key);
    }

    public FormModel model() {
        return model;
    }

    public GridPane grid() {
        return grid;
    }

    /** Keeps long record values on one line so a combo never widens the dialog. */
    private static final class CompactCell extends javafx.scene.control.ListCell<Object> {
        @Override
        protected void updateItem(Object item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : Text.label(item));
        }
    }

    private static final class RegionSize {
        static double label() {
            return 130;
        }
    }
}
