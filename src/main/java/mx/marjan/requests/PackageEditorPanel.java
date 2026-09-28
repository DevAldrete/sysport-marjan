package mx.marjan.requests;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.Ui;

/**
 * In-form editor for a request's package list: add/edit/remove lines and see
 * the running total weight. The list lives in memory until the request form is
 * saved, when the service replaces the stored packages atomically.
 */
public final class PackageEditorPanel extends VBox {

    private final long requestId;
    private final List<CargoPackage> rows = new ArrayList<>();
    private final RecordTable<CargoPackage> table = new RecordTable<>(List.of(
            RecordTable.Column.text("Descripcion", CargoPackage::description, 40),
            RecordTable.Column.number("Cantidad", CargoPackage::quantity),
            RecordTable.Column.of("Unidad", pkg -> pkg.unit() == null ? "" : pkg.unit().label()),
            RecordTable.Column.number("Peso/u", CargoPackage::unitWeight),
            RecordTable.Column.number("Peso total", CargoPackage::totalWeight)));
    private final Label total = new Label();
    private Runnable onChange = () -> {};

    public PackageEditorPanel(long requestId, List<CargoPackage> initial) {
        super(6);
        this.requestId = requestId;
        rows.addAll(initial);
        table.setPrefHeight(150);
        total.getStyleClass().add("form-hint");
        HBox actions = new HBox(8,
                Ui.button("Agregar", "Agregar un paquete a la lista", this::addPackage),
                Ui.button("Editar", "Editar el paquete seleccionado", this::editPackage),
                Ui.button("Quitar", "Quitar el paquete seleccionado", this::removePackage));
        getChildren().addAll(new Label("Paquetes (bultos)"), table, actions, total);
        refresh();
    }

    /** The package list as it currently stands in the editor. */
    public List<CargoPackage> packages() {
        return List.copyOf(rows);
    }

    /** Notified whenever the list changes, so the form can refresh a total field. */
    public void setOnChange(Runnable listener) {
        this.onChange = listener == null ? () -> {} : listener;
    }

    /** Live total weight of the whole list (null when no line has a weight). */
    public BigDecimal totalWeight() {
        BigDecimal sum = null;
        for (CargoPackage pkg : rows) {
            BigDecimal line = pkg.totalWeight();
            if (line != null) {
                sum = sum == null ? line : sum.add(line);
            }
        }
        return sum;
    }

    private void addPackage() {
        showForm(null);
    }

    private void editPackage() {
        CargoPackage selected = table.selected();
        if (selected == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un paquete");
            return;
        }
        showForm(selected);
    }

    private void removePackage() {
        CargoPackage selected = table.selected();
        if (selected == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un paquete");
            return;
        }
        if (!Ui.confirm(Ui.windowOf(this), "Quitar el paquete seleccionado de la lista?")) {
            return;
        }
        int index = rows.indexOf(selected);
        if (index >= 0) {
            rows.remove(index);
            refresh();
        }
    }

    private void showForm(CargoPackage existing) {
        FormPanel form = new FormPanel()
                .addText("description", "Descripcion",
                        existing == null ? "" : existing.description(),
                        "Que contiene el paquete, ej. Refrigeradores")
                .addText("quantity", "Cantidad",
                        existing == null ? "1" : existing.quantity().toPlainString(),
                        "Piezas o bultos, ej. 40")
                .addCombo("unit", "Unidad", PackageUnit.values(),
                        existing == null || existing.unit() == null ? PackageUnit.BOX : existing.unit())
                .addText("unitWeight", "Peso por unidad (kg)",
                        existing == null || existing.unitWeight() == null
                                ? "" : existing.unitWeight().toPlainString(),
                        "En kilogramos, ej. 300; vacio si no se conoce");
        form.validate("quantity", Validators.number());
        form.validate("unitWeight", Validators.number());
        ModalForm.show(Ui.windowOf(this), existing == null ? "Agregar paquete" : "Editar paquete",
                form, () -> {
                    String description = form.text("description");
                    if (description.isBlank()) {
                        return Result.err("La descripcion del paquete es obligatoria");
                    }
                    BigDecimal quantity = Numbers.parseOrZero(form.text("quantity"));
                    if (quantity == null || quantity.signum() <= 0) {
                        return Result.err("La cantidad debe ser un numero mayor a cero");
                    }
                    BigDecimal unitWeight = null;
                    if (!form.text("unitWeight").isBlank()) {
                        unitWeight = Numbers.parseOrZero(form.text("unitWeight"));
                        if (unitWeight == null || unitWeight.signum() < 0) {
                            return Result.err("El peso por unidad debe ser un numero mayor o igual a cero");
                        }
                    }
                    PackageUnit unit = (PackageUnit) form.selected("unit");
                    if (existing == null) {
                        rows.add(new CargoPackage(0, requestId, rows.size() + 1, description,
                                quantity, unit, unitWeight, null, null));
                    } else {
                        int index = rows.indexOf(existing);
                        rows.set(index, new CargoPackage(existing.id(), requestId, index + 1,
                                description, quantity, unit, unitWeight,
                                existing.receivedQuantity(), existing.receiptCondition()));
                    }
                    return Result.ok(null);
                }, this::refresh);
    }

    private void refresh() {
        table.setRows(rows);
        BigDecimal total = totalWeight();
        this.total.setText(rows.size() + " linea(s) - Peso total: "
                + (total == null ? "-" : total.stripTrailingZeros().toPlainString() + " kg"));
        onChange.run();
    }
}
