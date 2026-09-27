package mx.marjan.fleet;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.layout.VBox;
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.trips.Trip;
import mx.marjan.trips.TripService;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.Icons;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class FuelLoadsView extends BaseView {

    private final FuelService service = new FuelService();
    private final VehicleService vehicleService = new VehicleService();
    private final TripService tripService = new TripService();
    private final RecordTable<FuelLoad> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Unidad", FuelLoad::vehicleLabel),
            RecordTable.Column.of("Viaje", FuelLoad::tripLabel),
            RecordTable.Column.of("Fecha", load -> Dates.format(load.loadDate())),
            RecordTable.Column.of("Estacion", FuelLoad::fuelStation),
            RecordTable.Column.number("Litros", FuelLoad::liters),
            RecordTable.Column.number("Precio/L", FuelLoad::pricePerLiter),
            RecordTable.Column.money("Importe", FuelLoad::amount),
            RecordTable.Column.number("Odometro", FuelLoad::odometerReading)));

    public FuelLoadsView() {
        var nuevo = Ui.primary("Nueva carga", this::openNew);
        nuevo.setGraphic(Icons.action(Feather.PLUS));
        var actions = Ui.toolbar(nuevo,
                Ui.button("Eliminar", this::deleteLoad),
                Ui.button("Recargar", this::reload));
        setTop(new VBox(actions));
        setCenter(table);
        reload();
    }

    @Override
    public void reload() {
        loadRows(service::listAll, table::setRows);
    }

    private void openNew() {
        Async.run(() -> new Object[] { vehicleService.search(null), tripService.search(null, null) },
                data -> {
                    @SuppressWarnings("unchecked")
                    List<Vehicle> vehicles = (List<Vehicle>) data[0];
                    @SuppressWarnings("unchecked")
                    List<Trip> trips = (List<Trip>) data[1];
                    if (vehicles.isEmpty()) {
                        Ui.info(Ui.windowOf(this), "No hay unidades registradas");
                        return;
                    }
                    showNewForm(vehicles, trips);
                },
                failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void showNewForm(List<Vehicle> vehicles, List<Trip> trips) {
        List<Object> tripOptions = new ArrayList<>();
        tripOptions.add("(sin viaje)");
        tripOptions.addAll(trips);
        Vehicle initial = vehicles.get(0);

        FormPanel form = new FormPanel();
        form.addCombo("vehicle", "Unidad", vehicles.toArray(), initial);
        form.addCombo("trip", "Viaje (opcional)", tripOptions.toArray(), "(sin viaje)");
        form.addText("station", "Estacion de servicio", "", "Nombre o numero de la estacion");
        form.addText("date", "Fecha y hora", Dates.format(Dates.now()), "Formato: AAAA-MM-DD HH:MM");
        form.addText("liters", "Litros", "0", "Litros cargados, ej. 45.5");
        form.addText("price", "Precio por litro", "0", "Precio unitario, ej. 24.90");
        form.addComputed("amount", "Importe", () -> Money.format(amountFor(form)));
        form.addText("odometer", "Odometro (km)", Numbers.plain(initial.mileage()),
                "Lectura del tablero; se propone el ultimo kilometraje de la unidad");
        form.validate("date", Validators.dateTime());
        form.validate("liters", Validators.number());
        form.validate("price", Validators.number());
        form.validate("odometer", Validators.number());
        form.onSelect("vehicle", () -> {
            if (form.selected("vehicle") instanceof Vehicle vehicle) {
                form.setText("odometer", Numbers.plain(vehicle.mileage()));
            }
        });

        ModalForm.show(Ui.windowOf(this), "Nueva carga de combustible", form, () -> {
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

    private void deleteLoad() {
        FuelLoad load = table.selected();
        if (load == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una carga");
            return;
        }
        Ui.delete(Ui.windowOf(this), "la carga seleccionada", () -> service.delete(load.id()), this::reload);
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
