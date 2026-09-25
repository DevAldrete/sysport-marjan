package mx.marjan.fleet;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

public class VehiclesView extends BaseView {

    private final VehicleService service = new VehicleService();
    private final MaintenanceService maintenanceService = new MaintenanceService();
    private final RecordTableModel<Vehicle> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Economico", Vehicle::internalCode),
            RecordTableModel.Column.of("Placas", Vehicle::plates),
            RecordTableModel.Column.of("Marca", Vehicle::brand),
            RecordTableModel.Column.of("Modelo", Vehicle::model),
            RecordTableModel.Column.of("Anio", Vehicle::year),
            RecordTableModel.Column.of("Capacidad", Vehicle::loadCapacity),
            RecordTableModel.Column.of("Kilometraje", Vehicle::mileage),
            RecordTableModel.Column.of("Estado", vehicle -> vehicle.status().label())));
    private final JTable table = Ui.table(model);
    private final JTextField searchField = new JTextField(18);

    public VehiclesView() {
        add(Ui.row(new JLabel("Buscar:"), searchField,
                Ui.button("Buscar", this::reload),
                Ui.button("Nuevo", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Cambiar estado", this::changeStatus),
                Ui.button("Mantenimiento", this::openMaintenance),
                Ui.button("Eliminar", this::deleteVehicle),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    @Override
    public void reload() {
        String term = searchField.getText();
        load(() -> service.search(term), model::setRows);
    }

    private Vehicle selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void openNew() {
        openForm(Vehicle.empty());
    }

    private void openEdit() {
        Vehicle vehicle = selected();
        if (vehicle == null) {
            Ui.info(this, "Seleccione una unidad");
            return;
        }
        openForm(vehicle);
    }

    private void openForm(Vehicle vehicle) {
        boolean isNew = vehicle.id() == 0;
        FormPanel form = new FormPanel()
                .addText("code", "No. economico", vehicle.internalCode())
                .addText("plates", "Placas", vehicle.plates())
                .addText("brand", "Marca", vehicle.brand())
                .addText("model", "Modelo", vehicle.model())
                .addText("year", "Anio", vehicle.year() == null ? "" : vehicle.year().toString())
                .addText("serial", "No. de serie", vehicle.serialNumber())
                .addText("type", "Tipo de unidad", vehicle.vehicleType())
                .addText("capacity", "Capacidad de carga (kg)", plain(vehicle.loadCapacity()))
                .addText("mileage", "Kilometraje", plain(vehicle.mileage()))
                .addCombo("status", "Estado", VehicleStatus.values(), vehicle.status());
        ModalForm.show(this, isNew ? "Nueva unidad" : "Editar unidad", form, () -> {
            Integer year = null;
            if (!form.text("year").isBlank()) {
                try {
                    year = Integer.parseInt(form.text("year"));
                } catch (NumberFormatException failure) {
                    return Result.err("El anio debe ser un numero");
                }
            }
            BigDecimal capacity = number(form.text("capacity"));
            BigDecimal mileage = number(form.text("mileage"));
            if (capacity == null || mileage == null) {
                return Result.err("Capacidad y kilometraje deben ser numeros");
            }
            Vehicle built = new Vehicle(vehicle.id(), form.text("code"), form.text("plates"),
                    form.text("brand"), form.text("model"), year, form.text("serial"),
                    form.text("type"), capacity, mileage, (VehicleStatus) form.selected("status"));
            return service.save(built);
        }, this::reload);
    }

    private void changeStatus() {
        Vehicle vehicle = selected();
        if (vehicle == null) {
            Ui.info(this, "Seleccione una unidad");
            return;
        }
        FormPanel form = new FormPanel().addCombo("status", "Nuevo estado",
                VehicleStatus.values(), vehicle.status());
        ModalForm.show(this, "Cambiar estado de " + vehicle.label(), form,
                () -> service.setStatus(vehicle.id(), (VehicleStatus) form.selected("status")),
                this::reload);
    }

    private void deleteVehicle() {
        Vehicle vehicle = selected();
        if (vehicle == null) {
            Ui.info(this, "Seleccione una unidad");
            return;
        }
        Ui.delete(this, "la unidad \"" + vehicle.label() + "\"",
                () -> service.delete(vehicle.id()), this::reload);
    }

    private void openMaintenance() {
        Vehicle vehicle = selected();
        if (vehicle == null) {
            Ui.info(this, "Seleccione una unidad");
            return;
        }
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Mantenimiento de " + vehicle.label(), Dialog.ModalityType.APPLICATION_MODAL);
        RecordTableModel<Maintenance> records = new RecordTableModel<>(List.of(
                RecordTableModel.Column.of("Fecha", record -> Dates.format(record.maintenanceDate())),
                RecordTableModel.Column.of("Tipo", record -> record.type().label()),
                RecordTableModel.Column.text("Trabajos", Maintenance::workPerformed, 50),
                RecordTableModel.Column.text("Proveedor", Maintenance::provider, 30),
                RecordTableModel.Column.of("Costo", record -> Money.format(record.cost())),
                RecordTableModel.Column.of("Proxima fecha", record -> Dates.format(record.nextServiceDate())),
                RecordTableModel.Column.of("Proximo km", Maintenance::nextServiceKm)));
        JTable recordsTable = Ui.table(records);
        Maintenance[] cache = new Maintenance[1];
        recordsTable.getSelectionModel().addListSelectionListener(event -> {
            int row = recordsTable.getSelectedRow();
            cache[0] = row < 0 ? null : records.rowAt(recordsTable.convertRowIndexToModel(row));
        });
        Runnable reloadRecords = () -> Async.run(() -> maintenanceService.listByVehicle(vehicle.id()),
                records::setRows, failure -> Ui.failure(dialog, failure));
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(Ui.scroll(recordsTable), BorderLayout.CENTER);
        dialog.add(Ui.row(Ui.button("Registrar mantenimiento",
                () -> openMaintenanceForm(vehicle, reloadRecords)),
                Ui.button("Eliminar", () -> {
                    if (cache[0] == null) {
                        Ui.info(dialog, "Seleccione un registro de mantenimiento");
                        return;
                    }
                    Ui.delete(dialog, "el registro de mantenimiento seleccionado",
                            () -> maintenanceService.delete(cache[0].id()), reloadRecords);
                }),
                Ui.button("Cerrar", dialog::dispose)), BorderLayout.SOUTH);
        reloadRecords.run();
        dialog.setSize(820, 380);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openMaintenanceForm(Vehicle vehicle, Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addText("date", "Fecha (yyyy-MM-dd)", Dates.format(Dates.today()))
                .addText("odometer", "Odometro", plain(vehicle.mileage()))
                .addCombo("type", "Tipo", MaintenanceType.values(), MaintenanceType.PREVENTIVE)
                .addArea("work", "Trabajos realizados", "")
                .addText("provider", "Proveedor / taller", "")
                .addText("cost", "Costo", "0")
                .addText("nextDate", "Proxima fecha (opcional)", "")
                .addText("nextKm", "Proximo km (opcional)", "");
        ModalForm.show(this, "Mantenimiento de " + vehicle.label(), form, () -> {
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            if (date == null) {
                return Result.err("La fecha es obligatoria (yyyy-MM-dd)");
            }
            BigDecimal odometer = number(form.text("odometer"));
            BigDecimal cost = Money.parse(form.text("cost")).orElse(BigDecimal.ZERO);
            LocalDate nextDate = form.text("nextDate").isBlank() ? null
                    : Dates.parseDate(form.text("nextDate")).orElse(null);
            BigDecimal nextKm = form.text("nextKm").isBlank() ? null : number(form.text("nextKm"));
            Maintenance record = new Maintenance(0, vehicle.id(), vehicle.label(), date, odometer,
                    (MaintenanceType) form.selected("type"), form.text("work"), form.text("provider"),
                    cost, nextDate, nextKm);
            return maintenanceService.register(record);
        }, onSaved);
    }

    private BigDecimal number(String text) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException failure) {
            return null;
        }
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
