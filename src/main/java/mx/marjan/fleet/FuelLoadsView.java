package mx.marjan.fleet;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;
import mx.marjan.trips.Trip;
import mx.marjan.trips.TripService;

public class FuelLoadsView extends BaseView {

    private final FuelService service = new FuelService();
    private final VehicleService vehicleService = new VehicleService();
    private final TripService tripService = new TripService();
    private final RecordTableModel<FuelLoad> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Unidad", FuelLoad::vehicleLabel),
            RecordTableModel.Column.of("Viaje", FuelLoad::tripLabel),
            RecordTableModel.Column.of("Fecha", load -> Dates.format(load.loadDate())),
            RecordTableModel.Column.of("Estacion", FuelLoad::fuelStation),
            RecordTableModel.Column.of("Litros", FuelLoad::liters),
            RecordTableModel.Column.of("Precio/L", FuelLoad::pricePerLiter),
            RecordTableModel.Column.of("Importe", load -> Money.format(load.amount())),
            RecordTableModel.Column.of("Odometro", FuelLoad::odometerReading)));
    private final JTable table = Ui.table(model);

    private final FuelLoad[] selectedLoad = new FuelLoad[1];

    public FuelLoadsView() {
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            selectedLoad[0] = row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
        });
        add(Ui.row(Ui.button("Nueva carga", this::openNew),
                Ui.button("Eliminar", this::deleteLoad),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    private void deleteLoad() {
        if (selectedLoad[0] == null) {
            Ui.info(this, "Seleccione una carga");
            return;
        }
        Ui.delete(this, "la carga seleccionada",
                () -> service.delete(selectedLoad[0].id()), this::reload);
    }

    @Override
    public void reload() {
        load(service::listAll, model::setRows);
    }

    private void openNew() {
        mx.marjan.shared.Async.run(
                () -> new Object[] { vehicleService.search(null), tripService.search(null, null) },
                data -> {
                    @SuppressWarnings("unchecked")
                    List<Vehicle> vehicles = (List<Vehicle>) data[0];
                    @SuppressWarnings("unchecked")
                    List<Trip> trips = (List<Trip>) data[1];
                    if (vehicles.isEmpty()) {
                        Ui.info(this, "No hay unidades registradas");
                        return;
                    }
                    showNewForm(vehicles, trips);
                },
                failure -> Ui.failure(this, failure));
    }

    private void showNewForm(List<Vehicle> vehicles, List<Trip> trips) {
        List<Object> tripOptions = new ArrayList<>();
        tripOptions.add("(sin viaje)");
        tripOptions.addAll(trips);
        FormPanel form = new FormPanel()
                .addCombo("vehicle", "Unidad", vehicles.toArray(), vehicles.get(0))
                .addCombo("trip", "Viaje (opcional)", tripOptions.toArray(), "(sin viaje)")
                .addText("station", "Estacion de servicio", "")
                .addText("date", "Fecha y hora (yyyy-MM-dd HH:mm)", Dates.format(Dates.now()))
                .addText("liters", "Litros", "0")
                .addText("price", "Precio por litro", "0")
                .addText("amount", "Importe", "0")
                .addText("odometer", "Odometro", "0");
        ModalForm.show(this, "Nueva carga de combustible", form, () -> {
            Vehicle vehicle = (Vehicle) form.selected("vehicle");
            Object tripValue = form.selected("trip");
            Long tripId = tripValue instanceof Trip trip ? trip.id() : null;
            LocalDateTime date = Dates.parseDateTime(form.text("date")).orElse(null);
            if (date == null) {
                return Result.err("La fecha es obligatoria (yyyy-MM-dd HH:mm)");
            }
            BigDecimal liters = number(form.text("liters"));
            BigDecimal price = number(form.text("price"));
            BigDecimal amount = number(form.text("amount"));
            BigDecimal odometer = number(form.text("odometer"));
            if (liters == null || price == null || amount == null || odometer == null) {
                return Result.err("Litros, precio, importe y odometro deben ser numeros");
            }
            FuelLoad load = new FuelLoad(0, vehicle.id(), vehicle.label(), tripId,
                    tripValue instanceof Trip trip ? trip.folio() : "", form.text("station"),
                    date, liters, price, amount, odometer);
            return service.register(load);
        }, this::reload);
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
}
