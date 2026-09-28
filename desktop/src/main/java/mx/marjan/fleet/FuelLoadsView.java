package mx.marjan.fleet;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JTable;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;
import mx.marjan.shared.Validators;
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

    public FuelLoadsView() {
        add(Ui.row(Ui.button("Nueva carga", this::openNew),
                Ui.button("Eliminar", this::deleteLoad),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    private void deleteLoad() {
        FuelLoad load = selectedRow(table, model);
        if (load == null) {
            Ui.info(this, "Seleccione una carga");
            return;
        }
        Ui.delete(this, "la carga seleccionada", () -> service.delete(load.id()), this::reload);
    }

    @Override
    public void reload() {
        loadRows(service::listAll, model::setRows);
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
        Vehicle initial = vehicles.get(0);
        FormPanel form = new FormPanel()
                .addCombo("vehicle", "Unidad", vehicles.toArray(), initial)
                .addCombo("trip", "Viaje (opcional)", tripOptions.toArray(), "(sin viaje)")
                .addText("station", "Estacion de servicio", "", "Nombre o numero de la estacion")
                .addText("date", "Fecha y hora", Dates.format(Dates.now()), "Formato: AAAA-MM-DD HH:MM")
                .addText("liters", "Litros", "0", "Litros cargados, ej. 45.5")
                .addText("price", "Precio por litro", "0", "Precio unitario, ej. 24.90");
        form.addComputed("amount", "Importe", () -> Money.format(amountFor(form)));
        form.addText("odometer", "Odometro (km)", Numbers.plain(initial.mileage()),
                "Lectura del tablero; se propone el ultimo kilometraje de la unidad");
        form.validate("date", Validators.dateTime());
        form.validate("liters", Validators.number());
        form.validate("price", Validators.number());
        form.validate("odometer", Validators.number());
        form.onSelect("vehicle", () -> {
            Vehicle vehicle = (Vehicle) form.selected("vehicle");
            if (vehicle != null) {
                form.setText("odometer", Numbers.plain(vehicle.mileage()));
            }
        });
        ModalForm.show(this, "Nueva carga de combustible", form, () -> {
            Vehicle vehicle = (Vehicle) form.selected("vehicle");
            Object tripValue = form.selected("trip");
            Long tripId = tripValue instanceof Trip trip ? trip.id() : null;
            LocalDateTime date = Dates.parseDateTime(form.text("date")).orElse(null);
            if (date == null) {
                return Result.err("La fecha es obligatoria (AAAA-MM-DD HH:MM)");
            }
            BigDecimal liters = Numbers.parseOrZero(form.text("liters"));
            BigDecimal price = Numbers.parseOrZero(form.text("price"));
            BigDecimal odometer = Numbers.parseOrZero(form.text("odometer"));
            if (liters == null || price == null || odometer == null) {
                return Result.err("Litros, precio y odometro deben ser numeros");
            }
            FuelLoad load = new FuelLoad(0, vehicle.id(), vehicle.label(), tripId,
                    tripValue instanceof Trip trip ? trip.folio() : "", form.text("station"),
                    date, liters, price, amountFor(form), odometer);
            return service.register(load);
        }, this::reload);
    }

    /** The importe is always liters x price; the database rejects a mismatch. */
    private static BigDecimal amountFor(FormPanel form) {
        BigDecimal liters = Numbers.parseOrZero(form.text("liters"));
        BigDecimal price = Numbers.parseOrZero(form.text("price"));
        if (liters == null || price == null) {
            return BigDecimal.ZERO;
        }
        return liters.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }
}
