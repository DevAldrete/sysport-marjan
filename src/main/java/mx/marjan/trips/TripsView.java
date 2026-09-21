package mx.marjan.trips;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import mx.marjan.fleet.Vehicle;
import mx.marjan.operators.Employee;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

public class TripsView extends BaseView {

    private final TripService service = new TripService();
    private final RecordTableModel<Trip> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Folio", Trip::folio),
            RecordTableModel.Column.of("Cliente", Trip::clientName),
            RecordTableModel.Column.of("Ruta", Trip::routeLabel),
            RecordTableModel.Column.of("Unidad", Trip::vehicleLabel),
            RecordTableModel.Column.of("Operador", Trip::employeeName),
            RecordTableModel.Column.of("Inicio", trip -> Dates.format(trip.plannedStart())),
            RecordTableModel.Column.of("Fin", trip -> Dates.format(trip.plannedEnd())),
            RecordTableModel.Column.of("Estado", trip -> trip.status().label())));
    private final JTable table = Ui.table(model);
    private final JTextField searchField = new JTextField(14);
    private final JComboBox<Object> statusFilter = new JComboBox<>();

    public TripsView() {
        statusFilter.addItem("(todos)");
        for (TripStatus status : TripStatus.values()) {
            statusFilter.addItem(status);
        }
        add(Ui.row(new JLabel("Buscar:"), searchField, new JLabel("Estado:"), statusFilter,
                Ui.button("Buscar", this::reload),
                Ui.button("Salida", this::depart),
                Ui.button("Llegada", this::arrive),
                Ui.button("Reasignar", this::reassign),
                Ui.button("Cancelar", this::cancel),
                Ui.button("Detalle", this::detail),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    @Override
    public void reload() {
        Object status = statusFilter.getSelectedItem();
        load(() -> service.search(searchField.getText(), status instanceof TripStatus s ? s : null),
                model::setRows);
    }

    private Trip selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void depart() {
        Trip trip = selected();
        if (trip == null) {
            Ui.info(this, "Seleccione un viaje");
            return;
        }
        if (!Ui.confirm(this, "Registrar la salida del viaje " + trip.folio() + "?")) {
            return;
        }
        Async.run(() -> service.depart(trip.id()),
                this::run, failure -> Ui.failure(this, failure));
    }

    private void arrive() {
        Trip trip = selected();
        if (trip == null) {
            Ui.info(this, "Seleccione un viaje");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("km", "Kilometros reales", trip.estimatedKm() == null
                        ? "0" : trip.estimatedKm().toPlainString());
        ModalForm.show(this, "Llegada del viaje " + trip.folio(), form, () -> {
            BigDecimal km;
            try {
                km = new BigDecimal(form.text("km").isBlank() ? "0" : form.text("km"));
            } catch (NumberFormatException failure) {
                return Result.err("Los kilometros reales deben ser un numero");
            }
            return service.arrive(trip.id(), km);
        }, this::reload);
    }

    private void reassign() {
        Trip trip = selected();
        if (trip == null) {
            Ui.info(this, "Seleccione un viaje");
            return;
        }
        Async.run(() -> new java.util.AbstractMap.SimpleEntry<>(
                        service.eligibleVehicles(trip.plannedStart(), trip.plannedEnd()),
                        service.eligibleOperators(trip.plannedStart(), trip.plannedEnd())),
                pair -> {
                    if (pair.getKey().isEmpty() || pair.getValue().isEmpty()) {
                        Ui.info(this, "No hay unidades u operadores elegibles para el periodo");
                        return;
                    }
                    FormPanel form = new FormPanel()
                            .addCombo("vehicle", "Unidad", pair.getKey().toArray(), pair.getKey().get(0))
                            .addCombo("operator", "Operador", pair.getValue().toArray(), pair.getValue().get(0));
                    ModalForm.show(this, "Reasignar " + trip.folio(), form, () -> {
                        Vehicle vehicle = (Vehicle) form.selected("vehicle");
                        Employee operator = (Employee) form.selected("operator");
                        return service.reassign(trip.id(), vehicle.id(), operator.id());
                    }, this::reload);
                },
                failure -> Ui.failure(this, failure));
    }

    private void cancel() {
        Trip trip = selected();
        if (trip == null) {
            Ui.info(this, "Seleccione un viaje");
            return;
        }
        FormPanel form = new FormPanel().addArea("reason", "Motivo", "");
        ModalForm.show(this, "Cancelar " + trip.folio(), form,
                () -> service.cancel(trip.id(), form.text("reason")), this::reload);
    }

    private void detail() {
        Trip trip = selected();
        if (trip == null) {
            Ui.info(this, "Seleccione un viaje");
            return;
        }
        TripDetailDialog.show(this, trip);
    }

    private void run(Result<?> result) {
        if (result.isErr()) {
            Ui.error(this, "Error", result.problems());
        } else {
            reload();
        }
    }
}
