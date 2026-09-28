package mx.marjan.trips;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
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
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.RecordTablePanel;
import mx.marjan.ui.StatusBadge;
import mx.marjan.ui.StatusTones;
import mx.marjan.ui.ThemeManager;
import mx.marjan.ui.Ui;

/** Trip detail with tabs for costs, fuel, advances, incidents and delivery. */
final class TripDetailDialog {

    private final Trip trip;
    private final Stage stage = new Stage();
    private final ExpenseService expenseService = new ExpenseService();
    private final FuelService fuelService = new FuelService();
    private final AdvanceService advanceService = new AdvanceService();
    private final IncidentService incidentService = new IncidentService();
    private final DeliveryService deliveryService = new DeliveryService();
    private final CargoPackageService packageService = new CargoPackageService();

    static void show(Window parent, Trip trip) {
        new TripDetailDialog(trip).open(parent);
    }

    private TripDetailDialog(Trip trip) {
        this.trip = trip;
    }

    private void open(Window parent) {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                tab("Resumen", summaryPanel()),
                tab("Gastos", expensesPanel()),
                tab("Combustible", fuelPanel()),
                tab("Anticipos", advancesPanel()),
                tab("Incidencias", incidentsPanel()),
                tab("Paquetes", packagesPanel()),
                tab("Entrega", deliveryPanel()));

        BorderPane root = new BorderPane(tabs);
        root.setBottom(Ui.toolbar(Ui.button("Cerrar", stage::close)));
        BorderPane.setMargin(root.getBottom(), new Insets(0, 16, 12, 16));

        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Viaje " + trip.folio());
        Scene scene = new Scene(root, 940, 620);
        ThemeManager.apply(scene);
        stage.setScene(scene);
        stage.show();
    }

    private Tab tab(String title, Node content) {
        return new Tab(title, content);
    }

    private record Summary(BigDecimal expenses, BigDecimal fuel, AdvanceBalance advance) {}

    private Node summaryPanel() {
        VBox tripInfo = new VBox(6);
        Label tripTitle = new Label("Datos del viaje");
        tripTitle.getStyleClass().add("section-title");
        tripInfo.getChildren().addAll(tripTitle,
                kvNode("Estado", StatusBadge.of(trip.status().label(), StatusTones.trip(trip.status()))),
                kv("Unidad", trip.vehicleLabel()),
                kv("Operador", trip.employeeName()),
                kv("Ruta", trip.routeLabel()),
                kv("Cliente", trip.clientName()),
                kv("Periodo", Dates.format(trip.plannedStart()) + " a " + Dates.format(trip.plannedEnd())),
                kv("Salida", Dates.format(trip.departure())),
                kv("Llegada", Dates.format(trip.arrival())));

        VBox costs = new VBox(6);
        Label costsTitle = new Label("Costos");
        costsTitle.getStyleClass().add("section-title");
        Label expensesLabel = new Label("...");
        Label fuelLabel = new Label("...");
        Label totalLabel = new Label("...");
        Label advanceLabel = new Label("...");
        costs.getChildren().addAll(costsTitle,
                kvNode("Gastos", expensesLabel),
                kvNode("Combustible", fuelLabel),
                kvNode("Total", totalLabel),
                kvNode("Anticipo", advanceLabel));

        Async.run(() -> new Summary(
                expenseService.listByTrip(trip.id()).stream().map(Expense::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                fuelService.listByTrip(trip.id()).stream().map(FuelLoad::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                advanceService.balanceForTrip(trip.id())),
                summary -> {
                    expensesLabel.setText(Money.format(summary.expenses()));
                    fuelLabel.setText(Money.format(summary.fuel()));
                    totalLabel.setText(Money.format(summary.expenses().add(summary.fuel())));
                    advanceLabel.setText(summary.advance().label());
                },
                failure -> expensesLabel.setText("No se pudo cargar"));

        VBox box = new VBox(18, card(tripInfo), card(costs));
        box.setPadding(new Insets(16));
        return box;
    }

    private Node expensesPanel() {
        RecordTable<Expense> table = new RecordTable<>(List.of(
                RecordTable.Column.of("Fecha", expense -> Dates.format(expense.expenseDate())),
                RecordTable.Column.of("Tipo", expense -> expense.type().label()),
                RecordTable.Column.money("Importe", Expense::amount),
                RecordTable.Column.text("Descripcion", Expense::description, 50)));
        RecordTablePanel<Expense> panel = new RecordTablePanel<>(table);
        Runnable reload = () -> Async.run(() -> expenseService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(stage, failure));
        panel.withActions(
                Ui.button("Nuevo gasto", () -> openExpenseForm(reload)),
                Ui.button("Eliminar", () -> {
                    if (panel.selected() == null) {
                        Ui.info(stage, "Seleccione un gasto");
                        return;
                    }
                    Ui.delete(stage, "el gasto seleccionado",
                            () -> expenseService.delete(panel.selected().id()), reload);
                }),
                Ui.button("Recargar", reload));
        panel.setPadding(new Insets(16));
        reload.run();
        return panel;
    }

    private void openExpenseForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addCombo("type", "Tipo", ExpenseType.values(), ExpenseType.TOLLS)
                .addText("amount", "Importe", "0")
                .addText("date", "Fecha (yyyy-MM-dd)", Dates.format(Dates.today()))
                .addText("description", "Descripcion", "");
        form.validate("amount", mx.marjan.shared.Validators.money());
        form.validate("date", mx.marjan.shared.Validators.date());
        ModalForm.show(stage, "Gasto del viaje " + trip.folio(), form, () -> {
            Result<BigDecimal> amountResult = Money.require(form.text("amount"), "importe");
            if (amountResult.isErr()) {
                return amountResult;
            }
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            Expense expense = new Expense(0, trip.id(), trip.folio(),
                    (ExpenseType) form.selected("type"), amountResult.value(), date, form.text("description"));
            return expenseService.register(expense);
        }, onSaved);
    }

    private Node fuelPanel() {
        RecordTable<FuelLoad> table = new RecordTable<>(List.of(
                RecordTable.Column.of("Fecha", load -> Dates.format(load.loadDate())),
                RecordTable.Column.of("Estacion", FuelLoad::fuelStation),
                RecordTable.Column.number("Litros", FuelLoad::liters),
                RecordTable.Column.number("Precio/L", FuelLoad::pricePerLiter),
                RecordTable.Column.money("Importe", FuelLoad::amount),
                RecordTable.Column.number("Odometro", FuelLoad::odometerReading)));
        RecordTablePanel<FuelLoad> panel = new RecordTablePanel<>(table);
        Runnable reload = () -> Async.run(() -> fuelService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(stage, failure));
        panel.withActions(Ui.button("Recargar", reload));
        panel.setPadding(new Insets(16));
        reload.run();
        return panel;
    }

    private Node advancesPanel() {
        RecordTable<Advance> table = new RecordTable<>(List.of(
                RecordTable.Column.of("Operador", Advance::employeeName),
                RecordTable.Column.money("Monto", Advance::amountGiven),
                RecordTable.Column.of("Fecha", advance -> Dates.format(advance.deliveredDate())),
                RecordTable.Column.of("Estado", advance -> advance.status().label())));
        RecordTablePanel<Advance> panel = new RecordTablePanel<>(table);
        Runnable reload = () -> Async.run(() -> advanceService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(stage, failure));
        panel.withActions(
                Ui.button("Registrar anticipo", () -> openAdvanceForm(reload)),
                Ui.button("Comprobar", () -> settleAdvance(panel, reload)),
                Ui.button("Eliminar", () -> {
                    if (panel.selected() == null) {
                        Ui.info(stage, "Seleccione un anticipo");
                        return;
                    }
                    Ui.delete(stage, "el anticipo seleccionado",
                            () -> advanceService.delete(panel.selected().id()), reload);
                }),
                Ui.button("Recargar", reload));
        panel.setPadding(new Insets(16));
        reload.run();
        return panel;
    }

    private void settleAdvance(RecordTablePanel<Advance> panel, Runnable reload) {
        if (panel.selected() == null) {
            Ui.info(stage, "Seleccione un anticipo");
            return;
        }
        if (!Ui.confirm(stage, "\u00bfMarcar el anticipo como comprobado?")) {
            return;
        }
        Async.run(() -> advanceService.settle(panel.selected().id()), result -> {
            if (result.isErr()) {
                Ui.error(stage, "No se pudo comprobar el anticipo", result.problems());
            } else {
                reload.run();
            }
        }, failure -> Ui.failure(stage, failure));
    }

    private void openAdvanceForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addText("amount", "Monto entregado", "0")
                .addText("date", "Fecha de entrega (yyyy-MM-dd)", Dates.format(Dates.today()));
        form.validate("amount", mx.marjan.shared.Validators.money());
        form.validate("date", mx.marjan.shared.Validators.date());
        ModalForm.show(stage, "Anticipo del viaje " + trip.folio(), form, () -> {
            Result<BigDecimal> amountResult = Money.require(form.text("amount"), "monto entregado");
            if (amountResult.isErr()) {
                return amountResult;
            }
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            Advance advance = new Advance(0, trip.id(), trip.folio(), trip.employeeId(),
                    trip.employeeName(), amountResult.value(), date, AdvanceStatus.PENDING, null);
            return advanceService.register(advance);
        }, onSaved);
    }

    private Node incidentsPanel() {
        RecordTable<Incident> table = new RecordTable<>(List.of(
                RecordTable.Column.of("Fecha", incident -> Dates.format(incident.incidentDate())),
                RecordTable.Column.of("Tipo", incident -> incident.type().label()),
                RecordTable.Column.text("Ubicacion", Incident::location, 30),
                RecordTable.Column.text("Descripcion", Incident::description, 50),
                RecordTable.Column.text("Acciones", Incident::actionsTaken, 50)));
        RecordTablePanel<Incident> panel = new RecordTablePanel<>(table);
        Runnable reload = () -> Async.run(() -> incidentService.listByTrip(trip.id()),
                panel::setRows, failure -> Ui.failure(stage, failure));
        panel.withActions(
                Ui.button("Nueva incidencia", () -> openIncidentForm(reload)),
                Ui.button("Eliminar", () -> {
                    if (panel.selected() == null) {
                        Ui.info(stage, "Seleccione una incidencia");
                        return;
                    }
                    Ui.delete(stage, "la incidencia seleccionada",
                            () -> incidentService.delete(panel.selected().id()), reload);
                }),
                Ui.button("Recargar", reload));
        panel.setPadding(new Insets(16));
        reload.run();
        return panel;
    }

    private void openIncidentForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addText("date", "Fecha (yyyy-MM-dd)", Dates.format(Dates.today()))
                .addText("time", "Hora (HH:mm)", "")
                .addCombo("type", "Tipo", IncidentType.values(), IncidentType.OTHER)
                .addText("location", "Ubicacion", "")
                .addArea("description", "Descripcion", "")
                .addArea("actions", "Acciones realizadas", "");
        form.validate("date", mx.marjan.shared.Validators.date());
        ModalForm.show(stage, "Incidencia del viaje " + trip.folio(), form, () -> {
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            java.time.LocalTime time = null;
            if (!form.text("time").isBlank()) {
                try {
                    time = java.time.LocalTime.parse(form.text("time"));
                } catch (RuntimeException failure) {
                    return Result.err("La hora debe tener el formato HH:mm");
                }
            }
            Incident incident = new Incident(0, trip.id(), trip.folio(), date, time,
                    form.text("location"), (IncidentType) form.selected("type"),
                    form.text("description"), form.text("actions"));
            return incidentService.register(incident);
        }, onSaved);
    }

    private Node packagesPanel() {
        RecordTable<CargoPackage> table = new RecordTable<>(List.of(
                RecordTable.Column.text("Descripcion", CargoPackage::description, 40),
                RecordTable.Column.number("Cantidad", CargoPackage::quantity),
                RecordTable.Column.of("Unidad", pkg -> pkg.unit() == null ? "" : pkg.unit().label()),
                RecordTable.Column.number("Recibido", CargoPackage::receivedQuantity),
                RecordTable.Column.of("Condicion", pkg ->
                        pkg.receiptCondition() == null ? "" : pkg.receiptCondition().label())));
        RecordTablePanel<CargoPackage> panel = new RecordTablePanel<>(table);
        Runnable reload = () -> Async.run(() -> packageService.list(trip.serviceRequestId()),
                panel::setRows, failure -> Ui.failure(stage, failure));
        panel.withActions(
                Ui.button("Registrar recepcion", "Anotar cuanto llego y su condicion",
                        () -> openReceiptForm(panel, reload)),
                Ui.button("Recibir todo", "Marcar todos como completos", () -> receiveAll(reload)),
                Ui.button("Recargar", reload));
        panel.setPadding(new Insets(16));
        reload.run();
        return panel;
    }

    private void openReceiptForm(RecordTablePanel<CargoPackage> panel, Runnable reload) {
        CargoPackage selected = panel.selected();
        if (selected == null) {
            Ui.info(stage, "Seleccione un paquete");
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
        ModalForm.show(stage, "Recepcion del paquete", form, () -> {
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
        if (!Ui.confirm(stage, "\u00bfMarcar todos los paquetes como recibidos y completos?")) {
            return;
        }
        Async.run(() -> {
            List<CargoPackage> received = packageService.list(trip.serviceRequestId()).stream()
                    .map(line -> line.withReceipt(line.quantity(), PackageCondition.OK))
                    .toList();
            return packageService.saveReceipts(received);
        }, result -> {
            if (result.isErr()) {
                Ui.error(stage, "No se puede registrar", result.problems());
            } else {
                reload.run();
            }
        }, failure -> Ui.failure(stage, failure));
    }

    private Node deliveryPanel() {
        Label status = new Label("Cargando...");
        VBox box = new VBox(8, card(new VBox(6, sectionTitle("Entrega registrada"), status)),
                Ui.toolbar(Ui.button("Registrar / actualizar entrega", () -> openDeliveryForm(status)),
                        Ui.button("Recargar", () -> loadDelivery(status))));
        box.setPadding(new Insets(16));
        loadDelivery(status);
        return box;
    }

    private void loadDelivery(Label status) {
        Async.run(() -> deliveryService.findByTrip(trip.id()), delivery -> {
            if (delivery.isEmpty()) {
                status.setText("Sin entrega registrada");
            } else {
                Delivery record = delivery.get();
                status.setText("Fecha: " + Dates.format(record.actualDatetime())
                        + "\nRecibio: " + record.receivedBy()
                        + "\nEvidencia: " + record.evidenceReference()
                        + "\nEstado: " + record.status().label());
            }
        }, failure -> status.setText("Error al cargar: "
                + (failure.getMessage() == null ? failure.toString() : failure.getMessage())));
    }

    private void openDeliveryForm(Label status) {
        FormPanel form = new FormPanel()
                .addText("datetime", "Fecha y hora (yyyy-MM-dd HH:mm)", Dates.format(Dates.now()))
                .addText("receivedBy", "Recibio", "")
                .addText("evidence", "Referencia de evidencia", "")
                .addCombo("status", "Estado de la entrega", DeliveryStatus.values(), DeliveryStatus.COMPLETE);
        form.validate("datetime", Validators.dateTime());
        ModalForm.show(stage, "Entrega del viaje " + trip.folio(), form, () -> {
            java.time.LocalDateTime dateTime = Dates.parseDateTime(form.text("datetime")).orElse(null);
            if (dateTime == null) {
                return Result.err("La fecha y hora son obligatorias (yyyy-MM-dd HH:mm)");
            }
            Delivery delivery = new Delivery(0, trip.id(), trip.folio(), dateTime,
                    form.text("receivedBy"), form.text("evidence"),
                    (DeliveryStatus) form.selected("status"));
            return deliveryService.register(delivery);
        }, () -> loadDelivery(status));
    }

    private Node card(Node content) {
        VBox box = new VBox(content);
        box.getStyleClass().add("card");
        return box;
    }

    private Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private Node kv(String key, String value) {
        return kvNode(key, new Label(value == null || value.isBlank() ? "-" : value));
    }

    private Node kvNode(String key, Node value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("kpi-label");
        keyLabel.setMinWidth(110);
        keyLabel.setPrefWidth(110);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, keyLabel, spacer, value);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }
}
