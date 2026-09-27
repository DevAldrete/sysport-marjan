package mx.marjan.fleet;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.Icons;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.StatusTones;
import mx.marjan.ui.ThemeManager;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class VehiclesView extends BaseView {

    private final VehicleService service = new VehicleService();
    private final MaintenanceService maintenanceService = new MaintenanceService();
    private final RecordTable<Vehicle> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Economico", Vehicle::internalCode),
            RecordTable.Column.of("Placas", Vehicle::plates),
            RecordTable.Column.of("Marca", Vehicle::brand),
            RecordTable.Column.of("Modelo", Vehicle::model),
            RecordTable.Column.number("Anio", Vehicle::year),
            RecordTable.Column.number("Capacidad", Vehicle::loadCapacity),
            RecordTable.Column.number("Kilometraje", Vehicle::mileage),
            RecordTable.Column.badge("Estado", vehicle -> vehicle.status().label(),
                    vehicle -> StatusTones.vehicle(vehicle.status()))));
    private final TextField search = new TextField();

    public VehiclesView() {
        search.setPromptText("Economico, placas o marca");
        search.setOnAction(event -> reload());
        Ui.onDoubleClick(table, this::openForm);

        var nuevo = Ui.primary("Nuevo", this::openNew);
        nuevo.setGraphic(Icons.action(Feather.PLUS));
        var filters = Ui.filters(new Label("Buscar:"), search, Ui.button("Buscar", this::reload));
        var actions = Ui.toolbar(nuevo,
                Ui.button("Editar", this::openEdit),
                Ui.button("Cambiar estado", this::changeStatus),
                Ui.button("Mantenimiento", this::openMaintenance),
                Ui.button("Eliminar", this::deleteVehicle),
                Ui.button("Recargar", this::reload));
        setTop(new VBox(4, filters, actions));
        setCenter(table);
        reload();
    }

    @Override
    public void reload() {
        loadRows(() -> service.search(search.getText()), table::setRows);
    }

    private void openNew() {
        openForm(Vehicle.empty());
    }

    private void openEdit() {
        Vehicle vehicle = table.selected();
        if (vehicle == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una unidad");
            return;
        }
        openForm(vehicle);
    }

    private void openForm(Vehicle vehicle) {
        boolean isNew = vehicle.id() == 0;
        FormPanel form = new FormPanel()
                .addText("code", "No. economico", vehicle.internalCode(), "Identificador interno de la unidad")
                .addText("plates", "Placas", vehicle.plates())
                .addText("brand", "Marca", vehicle.brand())
                .addText("model", "Modelo", vehicle.model())
                .addText("year", "Anio", vehicle.year() == null ? "" : vehicle.year().toString(), "Ej. 2020")
                .addText("serial", "No. de serie", vehicle.serialNumber(), "VIN del vehiculo")
                .addText("type", "Tipo de unidad", vehicle.vehicleType())
                .addText("capacity", "Capacidad de carga (kg)", Numbers.plain(vehicle.loadCapacity()),
                        "En kilogramos")
                .addText("mileage", "Kilometraje", Numbers.plain(vehicle.mileage()), "Odometro actual en km");
        form.validate("year", Validators.number());
        form.validate("capacity", Validators.number());
        form.validate("mileage", Validators.number());
        ModalForm.show(Ui.windowOf(this), isNew ? "Nueva unidad" : "Editar unidad", form, () -> {
            Integer year = null;
            if (!form.text("year").isBlank()) {
                try {
                    year = Integer.parseInt(form.text("year"));
                } catch (NumberFormatException failure) {
                    return Result.err("El anio debe ser un numero");
                }
            }
            BigDecimal capacity = Numbers.parseOrZero(form.text("capacity"));
            BigDecimal mileage = Numbers.parseOrZero(form.text("mileage"));
            if (capacity == null || mileage == null) {
                return Result.err("Capacidad y kilometraje deben ser numeros");
            }
            Vehicle built = new Vehicle(vehicle.id(), form.text("code"), form.text("plates"),
                    form.text("brand"), form.text("model"), year, form.text("serial"),
                    form.text("type"), capacity, mileage, vehicle.status());
            return service.save(built);
        }, this::reload);
    }

    private void changeStatus() {
        Vehicle vehicle = table.selected();
        if (vehicle == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una unidad");
            return;
        }
        VehicleStatus[] options = VehicleStatus.manualValues();
        VehicleStatus initial = vehicle.status().isManual() ? vehicle.status() : options[0];
        FormPanel form = new FormPanel().addCombo("status", "Nuevo estado", options, initial);
        ModalForm.show(Ui.windowOf(this), "Cambiar estado de " + vehicle.label(), form,
                () -> service.setStatus(vehicle.id(), (VehicleStatus) form.selected("status")),
                this::reload);
    }

    private void deleteVehicle() {
        Vehicle vehicle = table.selected();
        if (vehicle == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una unidad");
            return;
        }
        Ui.delete(Ui.windowOf(this), "la unidad \"" + vehicle.label() + "\"",
                () -> service.delete(vehicle.id()), this::reload);
    }

    private void openMaintenance() {
        Vehicle vehicle = table.selected();
        if (vehicle == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una unidad");
            return;
        }
        RecordTable<Maintenance> records = new RecordTable<>(List.of(
                RecordTable.Column.of("Fecha", record -> Dates.format(record.maintenanceDate())),
                RecordTable.Column.of("Tipo", record -> record.type().label()),
                RecordTable.Column.text("Trabajos", Maintenance::workPerformed, 50),
                RecordTable.Column.text("Proveedor", Maintenance::provider, 30),
                RecordTable.Column.money("Costo", Maintenance::cost),
                RecordTable.Column.of("Proxima fecha", record -> Dates.format(record.nextServiceDate())),
                RecordTable.Column.number("Proximo km", Maintenance::nextServiceKm)));

        Runnable reload = () -> Async.run(() -> maintenanceService.listByVehicle(vehicle.id()),
                records::setRows, failure -> Ui.failure(Ui.windowOf(records), failure));
        Ui.onDoubleClick(records, record -> openMaintenanceForm(vehicle, record, reload));

        Stage stage = new Stage();
        stage.initOwner(Ui.windowOf(this));
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Mantenimiento de " + vehicle.label());
        var actions = Ui.toolbar(
                Ui.button("Registrar mantenimiento", () -> openMaintenanceForm(vehicle, null, reload)),
                Ui.button("Editar", () -> {
                    if (records.selected() == null) {
                        Ui.info(stage, "Seleccione un registro de mantenimiento");
                        return;
                    }
                    openMaintenanceForm(vehicle, records.selected(), reload);
                }),
                Ui.button("Eliminar", () -> {
                    if (records.selected() == null) {
                        Ui.info(stage, "Seleccione un registro de mantenimiento");
                        return;
                    }
                    Ui.delete(stage, "el registro de mantenimiento seleccionado",
                            () -> maintenanceService.delete(records.selected().id()), reload);
                }),
                Ui.button("Cerrar", stage::close));
        BorderPane root = new BorderPane(records);
        root.setPadding(new Insets(16));
        root.setBottom(actions);
        Scene scene = new Scene(root, 900, 500);
        ThemeManager.apply(scene);
        stage.setScene(scene);
        reload.run();
        stage.show();
    }

    private void openMaintenanceForm(Vehicle vehicle, Maintenance existing, Runnable onSaved) {
        Maintenance record = existing != null ? existing
                : new Maintenance(0, vehicle.id(), vehicle.label(), Dates.today(), vehicle.mileage(),
                        MaintenanceType.PREVENTIVE, "", "", BigDecimal.ZERO, null, null);
        FormPanel form = new FormPanel()
                .addText("date", "Fecha", Dates.format(record.maintenanceDate()), "Formato: AAAA-MM-DD")
                .addText("odometer", "Odometro", Numbers.plain(record.odometerReading()),
                        "Lectura del tablero en km")
                .addCombo("type", "Tipo", MaintenanceType.values(), record.type())
                .addArea("work", "Trabajos realizados", record.workPerformed(),
                        "Descripcion de lo realizado")
                .addText("provider", "Proveedor / taller", record.provider())
                .addText("cost", "Costo", Numbers.plain(record.cost()), "Importe del mantenimiento")
                .addText("nextDate", "Proxima fecha (opcional)", Dates.format(record.nextServiceDate()),
                        "Formato: AAAA-MM-DD")
                .addText("nextKm", "Proximo km (opcional)", Numbers.plain(record.nextServiceKm()),
                        "Kilometraje del proximo servicio");
        form.validate("date", Validators.date());
        form.validate("odometer", Validators.number());
        form.validate("cost", Validators.money());
        form.validate("nextDate", Validators.date());
        form.validate("nextKm", Validators.number());
        ModalForm.show(Ui.windowOf(this), "Mantenimiento de " + vehicle.label(), form, () -> {
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            if (date == null) {
                return Result.err("La fecha es obligatoria (yyyy-MM-dd)");
            }
            BigDecimal odometer = Numbers.parseOrZero(form.text("odometer"));
            BigDecimal cost = Money.parse(form.text("cost")).orElse(BigDecimal.ZERO);
            LocalDate nextDate = form.text("nextDate").isBlank() ? null
                    : Dates.parseDate(form.text("nextDate")).orElse(null);
            BigDecimal nextKm = form.text("nextKm").isBlank() ? null
                    : Numbers.parseOrZero(form.text("nextKm"));
            Maintenance built = new Maintenance(record.id(), vehicle.id(), vehicle.label(), date,
                    odometer, (MaintenanceType) form.selected("type"), form.text("work"),
                    form.text("provider"), cost, nextDate, nextKm);
            return maintenanceService.register(built);
        }, onSaved);
    }
}
