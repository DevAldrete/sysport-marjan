package mx.marjan.routes;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.RecordTablePanel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

/**
 * In-form editor for a route's ordered stop list: add, edit, remove and reorder
 * the stops. The list lives in memory until the route form is saved, when the
 * repository replaces the stored stops atomically.
 */
public final class RouteStopEditorPanel extends JPanel {

    private final long routeId;
    private final List<RouteStop> rows = new ArrayList<>();
    private final RecordTableModel<RouteStop> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("#", stop -> rows.indexOf(stop) + 1),
            RecordTableModel.Column.text("Parada", RouteStop::location, 60)));
    private final RecordTablePanel<RouteStop> table = new RecordTablePanel<>(model);

    public RouteStopEditorPanel(long routeId, List<RouteStop> initial) {
        super(new BorderLayout(4, 4));
        this.routeId = routeId;
        rows.addAll(initial);
        setBorder(BorderFactory.createTitledBorder("Recorrido (paradas en orden)"));
        JPanel actions = Ui.row(
                Ui.button("Agregar", "Agregar una parada al final", this::addStop),
                Ui.button("Editar", "Editar la parada seleccionada", this::editStop),
                Ui.button("Quitar", "Quitar la parada seleccionada", this::removeStop),
                Ui.button("Subir", "Mover la parada hacia arriba", () -> move(-1)),
                Ui.button("Bajar", "Mover la parada hacia abajo", () -> move(1)));
        add(table, BorderLayout.CENTER);
        add(Ui.column(actions, Ui.row(new JLabel(
                "El primer punto es el origen y el ultimo el destino"))), BorderLayout.SOUTH);
        setPreferredSize(new java.awt.Dimension(560, 200));
        refresh();
    }

    /** The stop list with sequence numbers normalized to its current order. */
    public List<RouteStop> stops() {
        List<RouteStop> ordered = new ArrayList<>();
        int sequence = 1;
        for (RouteStop stop : rows) {
            ordered.add(new RouteStop(stop.id(), routeId, sequence++, stop.location()));
        }
        return ordered;
    }

    private void addStop() {
        showForm(null);
    }

    private void editStop() {
        RouteStop selected = table.selected();
        if (selected == null) {
            Ui.info(this, "Seleccione una parada");
            return;
        }
        showForm(selected);
    }

    private void removeStop() {
        RouteStop selected = table.selected();
        if (selected == null) {
            Ui.info(this, "Seleccione una parada");
            return;
        }
        if (!Ui.confirm(this, "Quitar la parada seleccionada?")) {
            return;
        }
        rows.remove(selected);
        refresh();
    }

    private void move(int delta) {
        RouteStop selected = table.selected();
        if (selected == null) {
            Ui.info(this, "Seleccione una parada");
            return;
        }
        int index = rows.indexOf(selected);
        int target = index + delta;
        if (index < 0 || target < 0 || target >= rows.size()) {
            return;
        }
        rows.set(index, rows.get(target));
        rows.set(target, selected);
        refresh();
    }

    private void showForm(RouteStop existing) {
        FormPanel form = new FormPanel().addText("location", "Parada",
                existing == null ? "" : existing.location(), "Ciudad o punto de paso");
        ModalForm.show(this, existing == null ? "Agregar parada" : "Editar parada", form, () -> {
            String location = form.text("location");
            if (location.isBlank()) {
                return Result.err("La parada es obligatoria");
            }
            if (existing == null) {
                rows.add(new RouteStop(0, routeId, rows.size() + 1, location));
            } else {
                int index = rows.indexOf(existing);
                rows.set(index, new RouteStop(existing.id(), routeId, index + 1, location));
            }
            return Result.ok(null);
        }, this::refresh);
    }

    private void refresh() {
        model.setRows(rows);
    }
}
