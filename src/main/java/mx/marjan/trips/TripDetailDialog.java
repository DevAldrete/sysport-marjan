package mx.marjan.trips;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import mx.marjan.finance.Advance;
import mx.marjan.finance.AdvanceBalance;
import mx.marjan.finance.AdvanceService;
import mx.marjan.finance.AdvanceStatus;
import mx.marjan.finance.Expense;
import mx.marjan.finance.ExpenseService;
import mx.marjan.finance.ExpenseType;
import mx.marjan.fleet.FuelLoad;
import mx.marjan.fleet.FuelService;
import mx.marjan.requests.CargoPackage;
import mx.marjan.requests.CargoPackageService;
import mx.marjan.requests.PackageCondition;
import mx.marjan.shared.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Validators;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.RecordTablePanel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

/** Trip detail with tabs for costs, fuel, advances, incidents and delivery. */
public class TripDetailDialog extends JDialog {

    private final Trip trip;
    private final TripService tripService = new TripService();
    private final ExpenseService expenseService = new ExpenseService();
    private final FuelService fuelService = new FuelService();
    private final AdvanceService advanceService = new AdvanceService();
    private final IncidentService incidentService = new IncidentService();
    private final DeliveryService deliveryService = new DeliveryService();
    private final CargoPackageService packageService = new CargoPackageService();

    public static void show(Component parent, Trip trip) {
        new TripDetailDialog(parent, trip).setVisible(true);
    }

    private TripDetailDialog(Component parent, Trip trip) {
        super(SwingUtilities.getWindowAncestor(parent), "Viaje " + trip.folio(),
                Dialog.ModalityType.APPLICATION_MODAL);
        this.trip = trip;
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Resumen", summaryPanel());
        tabs.addTab("Paradas", stopsPanel());
        tabs.addTab("Gastos", expensesPanel());
        tabs.addTab("Combustible", fuelPanel());
        tabs.addTab("Anticipos", advancesPanel());
        tabs.addTab("Incidencias", incidentsPanel());
        tabs.addTab("Paquetes", packagesPanel());
        tabs.addTab("Entrega", deliveryPanel());
        setLayout(new BorderLayout(8, 8));
        add(tabs, BorderLayout.CENTER);
        add(Ui.row(Ui.button("Cerrar", this::dispose)), BorderLayout.SOUTH);
        setSize(880, 560);
        setLocationRelativeTo(parent);
    }

    private record Summary(BigDecimal expenses, BigDecimal fuel, AdvanceBalance advance) {}

    private JPanel summaryPanel() {
        JLabel tripInfo = new JLabel();
        JLabel costs = new JLabel();
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.titled("Viaje", tripInfo), BorderLayout.NORTH);
        panel.add(Ui.titled("Costos", costs), BorderLayout.CENTER);
        Async.run(() -> new Summary(
                expenseService.listByTrip(trip.id()).stream().map(Expense::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                fuelService.listByTrip(trip.id()).stream().map(FuelLoad::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                advanceService.balanceForTrip(trip.id())), summary -> {
            tripInfo.setText("<html>Unidad: " + trip.vehicleLabel() + "<br>Operador: " + trip.employeeName()
                    + "<br>Ruta: " + trip.routeLabel() + "<br>Estado: " + trip.status().label()
                    + "<br>Salida: " + Dates.format(trip.departure())
                    + "<br>Llegada: " + Dates.format(trip.arrival()) + "</html>");
            costs.setText("<html>Gastos: " + Money.format(summary.expenses())
                    + "<br>Combustible: " + Money.format(summary.fuel())
                    + "<br>Total: " + Money.format(summary.expenses().add(summary.fuel()))
                    + "<br>Anticipo: " + summary.advance().label()
                    + "</html>");
        }, failure -> Ui.failure(this, failure));
        return panel;
    }

    private JPanel stopsPanel() {
        RecordTableModel<TripStop> model = new RecordTableModel<>(List.of(
                RecordTableModel.Column.of("#", TripStop::sequenceNo),
                RecordTableModel.Column.text("Parada", TripStop::location, 40),
                RecordTableModel.Column.of("Llegada", stop -> stop.visited()
                        ? Dates.format(stop.arrivedAt()) : "Pendiente"),
                RecordTableModel.Column.text("Notas", stop -> stop.notes() == null ? "" : stop.notes(), 40)));
        RecordTablePanel<TripStop> panel = new RecordTablePanel<>(model);
        Runnable reload = () -> Async.run(() -> tripService.stops(trip.id()), panel::setRows,
                failure -> Ui.failure(this, failure));
        panel.withActions(
                Ui.button("Marcar llegada", "Registrar la llegada real a la parada",
                        () -> openStopArrivalForm(panel, reload)),
                Ui.button("Quitar llegada", "Borrar la llegada registrada en la parada",
                        () -> removeStopArrival(panel, reload)),
                Ui.button("Recargar", reload));
        reload.run();
        return panel;
    }

    private void openStopArrivalForm(RecordTablePanel<TripStop> panel, Runnable reload) {
        TripStop selected = panel.selected();
        if (selected == null) {
            Ui.info(this, "Seleccione una parada");
            return;
        }
        java.time.LocalDateTime initial = selected.visited() ? selected.arrivedAt() : Dates.now();
        FormPanel form = new FormPanel()
                .addDateTime("arrived", "Llegada", initial)
                .addText("notes", "Notas", selected.notes() == null ? "" : selected.notes());
        ModalForm.show(this, "Llegada a " + selected.location(), form, () -> {
            java.time.LocalDateTime arrived = form.dateTime("arrived");
            if (arrived == null) {
                return Result.err("La fecha y hora son obligatorias");
            }
            return tripService.saveStopArrival(trip.id(), selected.routeStopId(), arrived,
                    form.text("notes"));
        }, reload);
    }

    private void removeStopArrival(RecordTablePanel<TripStop> panel, Runnable reload) {
        TripStop selected = panel.selected();
        if (selected == null || !selected.visited()) {
            Ui.info(this, "Seleccione una parada con llegada registrada");
            return;
        }
        if (!Ui.confirm(this, "Quitar la llegada a " + selected.location() + "?")) {
            return;
        }
        Async.run(() -> tripService.deleteStopArrival(selected.arrivalId()), result -> {
            if (result.isErr()) {
                Ui.error(this, "No se puede quitar la llegada", result.problems());
            } else {
                reload.run();
            }
        }, failure -> Ui.failure(this, failure));
    }

    private JPanel expensesPanel() {
        RecordTableModel<Expense> model = new RecordTableModel<>(List.of(
                RecordTableModel.Column.of("Fecha", expense -> Dates.format(expense.expenseDate())),
                RecordTableModel.Column.of("Tipo", expense -> expense.type().label()),
                RecordTableModel.Column.of("Importe", expense -> Money.format(expense.amount())),
                RecordTableModel.Column.text("Descripcion", Expense::description, 50)));
        RecordTablePanel<Expense> panel = new RecordTablePanel<>(model);
        Runnable reload = () -> Async.run(() -> expenseService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(this, failure));
        panel.withActions(
                Ui.button("Nuevo gasto", () -> openExpenseForm(reload)),
                Ui.button("Eliminar", () -> {
                    if (panel.selected() == null) {
                        Ui.info(this, "Seleccione un gasto");
                        return;
                    }
                    Ui.delete(this, "el gasto seleccionado",
                            () -> expenseService.delete(panel.selected().id()), reload);
                }),
                Ui.button("Recargar", reload));
        reload.run();
        return panel;
    }

    private void openExpenseForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addCombo("type", "Tipo", ExpenseType.values(), ExpenseType.TOLLS)
                .addText("amount", "Importe", "0")
                .addDate("date", "Fecha", Dates.today())
                .addText("description", "Descripcion", "");
        ModalForm.show(this, "Gasto del viaje " + trip.folio(), form, () -> {
            Result<BigDecimal> amountResult = Money.require(form.text("amount"), "importe");
            if (amountResult.isErr()) {
                return amountResult;
            }
            LocalDate date = form.date("date");
            Expense expense = new Expense(0, trip.id(), trip.folio(),
                    (ExpenseType) form.selected("type"), amountResult.value(), date, form.text("description"));
            return expenseService.register(expense);
        }, onSaved);
    }

    private JPanel fuelPanel() {
        RecordTableModel<FuelLoad> model = new RecordTableModel<>(List.of(
                RecordTableModel.Column.of("Fecha", load -> Dates.format(load.loadDate())),
                RecordTableModel.Column.of("Estacion", FuelLoad::fuelStation),
                RecordTableModel.Column.of("Litros", FuelLoad::liters),
                RecordTableModel.Column.of("Precio/L", FuelLoad::pricePerLiter),
                RecordTableModel.Column.of("Importe", load -> Money.format(load.amount())),
                RecordTableModel.Column.of("Odometro", FuelLoad::odometerReading)));
        RecordTablePanel<FuelLoad> panel = new RecordTablePanel<>(model);
        Runnable reload = () -> Async.run(() -> fuelService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(this, failure));
        panel.withActions(Ui.button("Recargar", reload));
        reload.run();
        return panel;
    }

    private JPanel advancesPanel() {
        RecordTableModel<Advance> model = new RecordTableModel<>(List.of(
                RecordTableModel.Column.of("Operador", Advance::employeeName),
                RecordTableModel.Column.of("Monto", advance -> Money.format(advance.amountGiven())),
                RecordTableModel.Column.of("Fecha", advance -> Dates.format(advance.deliveredDate())),
                RecordTableModel.Column.of("Estado", advance -> advance.status().label())));
        RecordTablePanel<Advance> panel = new RecordTablePanel<>(model);
        Runnable reload = () -> Async.run(() -> advanceService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(this, failure));
        panel.withActions(
                Ui.button("Registrar anticipo", () -> openAdvanceForm(reload)),
                Ui.button("Comprobar", () -> settleAdvance(panel, reload)),
                Ui.button("Eliminar", () -> {
                    if (panel.selected() == null) {
                        Ui.info(this, "Seleccione un anticipo");
                        return;
                    }
                    Ui.delete(this, "el anticipo seleccionado",
                            () -> advanceService.delete(panel.selected().id()), reload);
                }),
                Ui.button("Recargar", reload));
        reload.run();
        return panel;
    }

    private void settleAdvance(RecordTablePanel<Advance> panel, Runnable reload) {
        if (panel.selected() == null) {
            Ui.info(this, "Seleccione un anticipo");
            return;
        }
        if (!Ui.confirm(this, "Marcar el anticipo como comprobado?")) {
            return;
        }
        Async.run(() -> advanceService.settle(panel.selected().id()), result -> {
            if (result.isErr()) {
                Ui.error(this, "No se pudo comprobar el anticipo", result.problems());
            } else {
                reload.run();
            }
        }, failure -> Ui.failure(this, failure));
    }

    private void openAdvanceForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addText("amount", "Monto entregado", "0")
                .addDate("date", "Fecha de entrega", Dates.today());
        ModalForm.show(this, "Anticipo del viaje " + trip.folio(), form, () -> {
            Result<BigDecimal> amountResult = Money.require(form.text("amount"), "monto entregado");
            if (amountResult.isErr()) {
                return amountResult;
            }
            LocalDate date = form.date("date");
            Advance advance = new Advance(0, trip.id(), trip.folio(), trip.employeeId(),
                    trip.employeeName(), amountResult.value(), date, AdvanceStatus.PENDING, null);
            return advanceService.register(advance);
        }, onSaved);
    }

    private JPanel incidentsPanel() {
        RecordTableModel<Incident> model = new RecordTableModel<>(List.of(
                RecordTableModel.Column.of("Fecha", incident -> Dates.format(incident.incidentDate())),
                RecordTableModel.Column.of("Tipo", incident -> incident.type().label()),
                RecordTableModel.Column.text("Ubicacion", Incident::location, 30),
                RecordTableModel.Column.text("Descripcion", Incident::description, 50),
                RecordTableModel.Column.text("Acciones", Incident::actionsTaken, 50)));
        RecordTablePanel<Incident> panel = new RecordTablePanel<>(model);
        Runnable reload = () -> Async.run(() -> incidentService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(this, failure));
        panel.withActions(
                Ui.button("Nueva incidencia", () -> openIncidentForm(reload)),
                Ui.button("Eliminar", () -> {
                    if (panel.selected() == null) {
                        Ui.info(this, "Seleccione una incidencia");
                        return;
                    }
                    Ui.delete(this, "la incidencia seleccionada",
                            () -> incidentService.delete(panel.selected().id()), reload);
                }),
                Ui.button("Recargar", reload));
        reload.run();
        return panel;
    }

    private void openIncidentForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addDateTime("when", "Fecha y hora", Dates.now())
                .addCombo("type", "Tipo", IncidentType.values(), IncidentType.OTHER)
                .addText("location", "Ubicacion", "")
                .addArea("description", "Descripcion", "")
                .addArea("actions", "Acciones realizadas", "");
        ModalForm.show(this, "Incidencia del viaje " + trip.folio(), form, () -> {
            java.time.LocalDateTime when = form.dateTime("when");
            LocalDate date = when == null ? null : when.toLocalDate();
            java.time.LocalTime time = when == null ? null : when.toLocalTime();
            Incident incident = new Incident(0, trip.id(), trip.folio(), date, time,
                    form.text("location"), (IncidentType) form.selected("type"),
                    form.text("description"), form.text("actions"));
            return incidentService.register(incident);
        }, onSaved);
    }

    private JPanel packagesPanel() {
        RecordTableModel<CargoPackage> model = new RecordTableModel<>(List.of(
                RecordTableModel.Column.text("Descripcion", CargoPackage::description, 40),
                RecordTableModel.Column.of("Cantidad", CargoPackage::quantity),
                RecordTableModel.Column.of("Unidad", pkg -> pkg.unit() == null ? "" : pkg.unit().label()),
                RecordTableModel.Column.of("Recibido", CargoPackage::receivedQuantity),
                RecordTableModel.Column.of("Condicion", pkg ->
                        pkg.receiptCondition() == null ? "" : pkg.receiptCondition().label())));
        RecordTablePanel<CargoPackage> panel = new RecordTablePanel<>(model);
        Runnable reload = () -> Async.run(() -> packageService.list(trip.serviceRequestId()),
                panel::setRows, failure -> Ui.failure(this, failure));
        panel.withActions(
                Ui.button("Registrar recepcion", "Anotar cuanto llego y su condicion", () -> openReceiptForm(panel, reload)),
                Ui.button("Recibir todo", "Marcar todos como completos", () -> receiveAll(reload)),
                Ui.button("Recargar", reload));
        reload.run();
        return panel;
    }

    private void openReceiptForm(RecordTablePanel<CargoPackage> panel, Runnable reload) {
        CargoPackage selected = panel.selected();
        if (selected == null) {
            Ui.info(this, "Seleccione un paquete");
            return;
        }
        BigDecimal received = selected.receivedQuantity() != null
                ? selected.receivedQuantity() : selected.quantity();
        FormPanel form = new FormPanel()
                .addText("received", "Cantidad recibida", received.toPlainString(),
                        "Entre 0 y la cantidad declarada")
                .addCombo("condition", "Condicion", PackageCondition.values(),
                        selected.receiptCondition() != null ? selected.receiptCondition() : PackageCondition.OK);
        form.validate("received", Validators.number());
        ModalForm.show(this, "Recepcion del paquete", form, () -> {
            BigDecimal quantity = Numbers.parseOrZero(form.text("received"));
            if (quantity == null || quantity.signum() < 0) {
                return Result.err("La cantidad recibida debe ser un numero mayor o igual a cero");
            }
            CargoPackage updated = selected.withReceipt(quantity,
                    (PackageCondition) form.selected("condition"));
            return packageService.saveReceipts(List.of(updated));
        }, reload);
    }

    private void receiveAll(Runnable reload) {
        if (!Ui.confirm(this, "Marcar todos los paquetes como recibidos y completos?")) {
            return;
        }
        Async.run(() -> {
            List<CargoPackage> received = packageService.list(trip.serviceRequestId()).stream()
                    .map(line -> line.withReceipt(line.quantity(), PackageCondition.OK))
                    .toList();
            return packageService.saveReceipts(received);
        }, result -> {
            if (result.isErr()) {
                Ui.error(this, "No se puede registrar", result.problems());
            } else {
                reload.run();
            }
        }, failure -> Ui.failure(this, failure));
    }

    private JPanel deliveryPanel() {
        JLabel status = new JLabel("Cargando...");
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.titled("Entrega registrada", status), BorderLayout.CENTER);
        panel.add(Ui.row(Ui.button("Registrar / actualizar entrega", () -> openDeliveryForm(status)),
                Ui.button("Recargar", () -> loadDelivery(status))), BorderLayout.SOUTH);
        loadDelivery(status);
        return panel;
    }

    private void loadDelivery(JLabel status) {
        Async.run(() -> deliveryService.findByTrip(trip.id()), delivery -> {
            if (delivery.isEmpty()) {
                status.setText("Sin entrega registrada");
            } else {
                Delivery record = delivery.get();
                status.setText("<html>Fecha: " + Dates.format(record.actualDatetime())
                        + "<br>Recibio: " + record.receivedBy()
                        + "<br>Evidencia: " + record.evidenceReference()
                        + "<br>Estado: " + record.status().label() + "</html>");
            }
        }, failure -> status.setText("Error al cargar: "
                + (failure.getMessage() == null ? failure.toString() : failure.getMessage())));
    }

    private void openDeliveryForm(JLabel status) {
        FormPanel form = new FormPanel()
                .addDateTime("datetime", "Fecha y hora", Dates.now())
                .addText("receivedBy", "Recibio", "")
                .addText("evidence", "Referencia de evidencia", "")
                .addCombo("status", "Estado de la entrega", DeliveryStatus.values(), DeliveryStatus.COMPLETE);
        ModalForm.show(this, "Entrega del viaje " + trip.folio(), form, () -> {
            java.time.LocalDateTime dateTime = form.dateTime("datetime");
            if (dateTime == null) {
                return Result.err("La fecha y hora son obligatorias");
            }
            Delivery delivery = new Delivery(0, trip.id(), trip.folio(), dateTime,
                    form.text("receivedBy"), form.text("evidence"),
                    (DeliveryStatus) form.selected("status"));
            return deliveryService.register(delivery);
        }, () -> loadDelivery(status));
    }
}
