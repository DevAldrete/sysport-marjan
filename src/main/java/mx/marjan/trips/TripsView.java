package mx.marjan.trips;

import java.math.BigDecimal;
import java.util.List;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.marjan.fleet.Vehicle;
import mx.marjan.operators.Employee;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.StatusTones;
import mx.marjan.ui.Ui;

public class TripsView extends BaseView {

    private final TripService service = new TripService();
    private final RecordTable<Trip> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Folio", Trip::folio),
            RecordTable.Column.of("Cliente", Trip::clientName),
            RecordTable.Column.text("Ruta", Trip::routeLabel, 40),
            RecordTable.Column.of("Unidad", Trip::vehicleLabel),
            RecordTable.Column.of("Operador", Trip::employeeName),
            RecordTable.Column.of("Inicio", trip -> Dates.format(trip.plannedStart())),
            RecordTable.Column.of("Fin", trip -> Dates.format(trip.plannedEnd())),
            RecordTable.Column.badge("Estado", trip -> trip.status().label(),
                    trip -> StatusTones.trip(trip.status()))));
    private final TextField search = new TextField();
    private final ComboBox<Object> statusFilter = new ComboBox<>();
    private final Button departButton = Ui.button("Salida", "Registrar la salida del viaje", this::depart);
    private final Button arriveButton = Ui.button("Llegada",
            "Registrar la llegada y los kilometros reales", this::arrive);
    private final Button reassignButton = Ui.button("Reasignar",
            "Cambiar la unidad o el operador", this::reassign);
    private final Button cancelButton = Ui.button("Cancelar", "Cancelar el viaje", this::cancel);
    private final Button deleteButton = Ui.button("Eliminar",
            "Eliminar el viaje y todo lo relacionado", this::deleteTrip);

    public TripsView() {
        search.setPromptText("Folio, cliente o unidad");
        search.setOnAction(event -> reload());
        statusFilter.getItems().add("(todos)");
        for (TripStatus status : TripStatus.values()) {
            statusFilter.getItems().add(status);
        }
        statusFilter.setValue("(todos)");
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> reload());
        Ui.onDoubleClick(table, trip -> detail());

        var filters = Ui.filters(new Label("Buscar:"), search,
                new Label("Estado:"), statusFilter,
                Ui.button("Buscar", this::reload));
        departButton.getStyleClass().add("accent");
        var actions = Ui.toolbar(departButton, arriveButton, reassignButton,
                Ui.button("Detalle", "Ver el detalle del viaje", this::detail),
                cancelButton, deleteButton,
                Ui.button("Recargar", this::reload));
        setTop(new VBox(4, filters, actions));
        setCenter(table);
        table.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, newValue) -> updateActions());
        updateActions();
        reload();
    }

    /** Enables each action only when it makes sense for the selected trip's status. */
    private void updateActions() {
        Trip trip = table.selected();
        TripStatus status = trip == null ? null : trip.status();
        boolean write = Session.has(Permissions.TRIPS_WRITE);
        boolean assign = Session.has(Permissions.TRIPS_ASSIGN);
        departButton.setDisable(!(write && status == TripStatus.SCHEDULED));
        arriveButton.setDisable(!(write && status == TripStatus.IN_TRANSIT));
        reassignButton.setDisable(!(assign && status == TripStatus.SCHEDULED));
        cancelButton.setDisable(!(write && status == TripStatus.SCHEDULED));
        deleteButton.setDisable(!(write
                && (status == TripStatus.SCHEDULED || status == TripStatus.CANCELLED)));
    }

    @Override
    public void reload() {
        Object status = statusFilter.getValue();
        loadRows(() -> {
            service.sweepLifecycle();
            return service.search(search.getText(), status instanceof TripStatus s ? s : null);
        }, rows -> {
            table.setRows(rows);
            updateActions();
        });
    }

    private Trip requireSelection() {
        Trip trip = table.selected();
        if (trip == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un viaje");
        }
        return trip;
    }

    private void depart() {
        Trip trip = requireSelection();
        if (trip == null) {
            return;
        }
        if (!Ui.confirm(Ui.windowOf(this), "\u00bfRegistrar la salida del viaje " + trip.folio() + "?")) {
            return;
        }
        Async.run(() -> service.depart(trip.id()), this::run, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void arrive() {
        Trip trip = requireSelection();
        if (trip == null) {
            return;
        }
        FormPanel form = new FormPanel()
                .addText("km", "Kilometros reales", trip.estimatedKm() == null
                        ? "0" : trip.estimatedKm().toPlainString(),
                        "Recorrido real; se propone el estimado de la ruta")
                .validate("km", Validators.number());
        ModalForm.show(Ui.windowOf(this), "Llegada del viaje " + trip.folio(), form, () -> {
            BigDecimal km = Numbers.parseOrZero(form.text("km"));
            if (km == null) {
                return Result.err("Los kilometros reales deben ser un numero");
            }
            return service.arrive(trip.id(), km);
        }, this::reload);
    }

    private void reassign() {
        Trip trip = requireSelection();
        if (trip == null) {
            return;
        }
        Async.run(() -> new Object[] {
                        service.eligibleVehicles(trip.plannedStart(), trip.plannedEnd()),
                        service.eligibleOperators(trip.plannedStart(), trip.plannedEnd()) },
                data -> {
                    @SuppressWarnings("unchecked")
                    List<Vehicle> vehicles = (List<Vehicle>) data[0];
                    @SuppressWarnings("unchecked")
                    List<Employee> operators = (List<Employee>) data[1];
                    if (vehicles.isEmpty() || operators.isEmpty()) {
                        Ui.info(Ui.windowOf(this), "No hay unidades u operadores elegibles para el periodo");
                        return;
                    }
                    FormPanel form = new FormPanel()
                            .addCombo("vehicle", "Unidad", vehicles.toArray(), vehicles.get(0))
                            .addCombo("operator", "Operador", operators.toArray(), operators.get(0));
                    ModalForm.show(Ui.windowOf(this), "Reasignar " + trip.folio(), form, () -> {
                        Vehicle vehicle = (Vehicle) form.selected("vehicle");
                        Employee operator = (Employee) form.selected("operator");
                        return service.reassign(trip.id(), vehicle.id(), operator.id());
                    }, this::reload);
                },
                failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void cancel() {
        Trip trip = requireSelection();
        if (trip == null) {
            return;
        }
        FormPanel form = new FormPanel().addArea("reason", "Motivo", "", "Razon de la cancelacion");
        ModalForm.show(Ui.windowOf(this), "Cancelar " + trip.folio(), form,
                () -> service.cancel(trip.id(), form.text("reason")), this::reload);
    }

    private void deleteTrip() {
        Trip trip = requireSelection();
        if (trip == null) {
            return;
        }
        Ui.delete(Ui.windowOf(this), "el viaje " + trip.folio()
                        + " y todo lo relacionado (gastos, anticipos, incidencias y entrega)",
                () -> service.delete(trip.id()), this::reload);
    }

    private void detail() {
        Trip trip = requireSelection();
        if (trip == null) {
            return;
        }
        TripDetailDialog.show(Ui.windowOf(this), trip);
    }

    private void run(Result<?> result) {
        if (result.isErr()) {
            Ui.error(Ui.windowOf(this), "No se pudo completar la operacion", result.problems());
        } else {
            reload();
        }
    }
}
