package mx.marjan.trips;

import mx.marjan.shared.Numbers;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import mx.marjan.fleet.Vehicle;
import mx.marjan.operators.Employee;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;
import mx.marjan.shared.Validators;

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
    private final JButton departButton = Ui.button("Salida", "Registrar la salida del viaje",
            this::depart, Session.has(Permissions.TRIPS_WRITE));
    private final JButton arriveButton = Ui.button("Llegada", "Registrar la llegada y los kilometros reales",
            this::arrive, Session.has(Permissions.TRIPS_WRITE));
    private final JButton reassignButton = Ui.button("Reasignar", "Cambiar la unidad o el operador",
            this::reassign, Session.has(Permissions.TRIPS_ASSIGN));
    private final JButton cancelButton = Ui.button("Cancelar", "Cancelar el viaje",
            this::cancel, Session.has(Permissions.TRIPS_WRITE));
    private final JButton deleteButton = Ui.button("Eliminar", "Eliminar el viaje y todo lo relacionado",
            this::deleteTrip, Session.has(Permissions.TRIPS_WRITE));

    public TripsView() {
        statusFilter.addItem("(todos)");
        for (TripStatus status : TripStatus.values()) {
            statusFilter.addItem(status);
        }
        Ui.onEnter(searchField, this::reload);
        Ui.onDoubleClick(table, this::detail);
        add(Ui.row(new JLabel("Buscar:"), searchField, new JLabel("Estado:"), statusFilter,
                Ui.button("Buscar", "Aplicar los filtros", this::reload),
                departButton, arriveButton, reassignButton, cancelButton,
                Ui.button("Detalle", "Ver el detalle del viaje", this::detail),
                deleteButton,
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        table.getSelectionModel().addListSelectionListener(event -> updateActions());
        updateActions();
        reload();
    }

    /** Enables each action only when it makes sense for the selected trip's status. */
    private void updateActions() {
        Trip trip = selected();
        TripStatus status = trip == null ? null : trip.status();
        boolean write = Session.has(Permissions.TRIPS_WRITE);
        boolean assign = Session.has(Permissions.TRIPS_ASSIGN);
        departButton.setEnabled(write && status == TripStatus.SCHEDULED);
        arriveButton.setEnabled(write && status == TripStatus.IN_TRANSIT);
        reassignButton.setEnabled(assign && status == TripStatus.SCHEDULED);
        cancelButton.setEnabled(write && status == TripStatus.SCHEDULED);
        deleteButton.setEnabled(write && (status == TripStatus.SCHEDULED || status == TripStatus.CANCELLED));
    }

    @Override
    public void reload() {
        Object status = statusFilter.getSelectedItem();
        loadRows(() -> {
            service.sweepLifecycle();
            return service.search(searchField.getText(), status instanceof TripStatus s ? s : null);
        }, rows -> {
            model.setRows(rows);
            updateActions();
        });
    }

    private Trip selected() {
        return selectedRow(table, model);
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
                        ? "0" : trip.estimatedKm().toPlainString(),
                        "Recorrido real; se propone el estimado de la ruta")
                .validate("km", Validators.number());
        ModalForm.show(this, "Llegada del viaje " + trip.folio(), form, () -> {
            BigDecimal km = Numbers.parseOrZero(form.text("km"));
            if (km == null) {
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
        FormPanel form = new FormPanel().addArea("reason", "Motivo", "", "Razon de la cancelacion");
        ModalForm.show(this, "Cancelar " + trip.folio(), form,
                () -> service.cancel(trip.id(), form.text("reason")), this::reload);
    }

    private void deleteTrip() {
        Trip trip = selected();
        if (trip == null) {
            Ui.info(this, "Seleccione un viaje");
            return;
        }
        if (trip.status() != TripStatus.SCHEDULED && trip.status() != TripStatus.CANCELLED) {
            Ui.info(this, "Solo se puede eliminar un viaje programado o cancelado. "
                    + "Use 'Cancelar' para los viajes en curso.");
            return;
        }
        Ui.delete(this, "el viaje " + trip.folio()
                        + " y todo lo relacionado (gastos, anticipos, incidencias y entrega)",
                () -> service.delete(trip.id()), this::reload);
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
            Ui.error(this, "No se pudo completar la operacion", result.problems());
        } else {
            reload();
        }
    }
}
