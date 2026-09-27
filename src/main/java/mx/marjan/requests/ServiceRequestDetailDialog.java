package mx.marjan.requests;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import mx.marjan.finance.AdvanceBalance;
import mx.marjan.finance.AdvanceService;
import mx.marjan.finance.Expense;
import mx.marjan.finance.ExpenseService;
import mx.marjan.finance.Invoice;
import mx.marjan.finance.InvoiceService;
import mx.marjan.fleet.FuelLoad;
import mx.marjan.fleet.FuelService;
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.trips.Delivery;
import mx.marjan.trips.DeliveryService;
import mx.marjan.trips.Trip;
import mx.marjan.trips.TripService;
import mx.marjan.ui.StatusBadge;
import mx.marjan.ui.StatusTones;
import mx.marjan.ui.ThemeManager;
import mx.marjan.ui.Ui;

/** FR-REQ-5: read-only "whole story" of a request: trip, costs, delivery and invoice. */
final class ServiceRequestDetailDialog {

    private final ServiceRequest request;
    private final TripService trips = new TripService();
    private final ExpenseService expenses = new ExpenseService();
    private final FuelService fuel = new FuelService();
    private final AdvanceService advances = new AdvanceService();
    private final DeliveryService deliveries = new DeliveryService();
    private final InvoiceService invoices = new InvoiceService();

    private ServiceRequestDetailDialog(ServiceRequest request) {
        this.request = request;
    }

    static void show(Window parent, ServiceRequest request) {
        new ServiceRequestDetailDialog(request).open(parent);
    }

    private record Detail(Optional<Trip> trip, List<Expense> expenses, List<FuelLoad> fuelLoads,
            AdvanceBalance advance, Optional<Delivery> delivery, Optional<Invoice> invoice) {}

    private void open(Window parent) {
        VBox body = new VBox(14);
        body.setPadding(new Insets(16));
        Label loading = new Label("Cargando...");
        loading.getStyleClass().add("placeholder-text");
        body.getChildren().add(loading);

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);

        BorderPane root = new BorderPane(scroll);
        root.setBottom(Ui.toolbar(Ui.button("Cerrar", () -> ((Stage) root.getScene().getWindow()).close())));

        Stage stage = new Stage();
        stage.initOwner(parent);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Detalle " + request.folio());
        Scene scene = new Scene(root, 620, 640);
        ThemeManager.apply(scene);
        stage.setScene(scene);

        Async.run(this::load, detail -> body.getChildren().setAll(render(detail)),
                failure -> body.getChildren().setAll(Ui.muted("No se pudo cargar el detalle")));

        stage.show();
    }

    private Detail load() {
        Optional<Trip> trip = trips.findByRequest(request.id());
        if (trip.isEmpty()) {
            return new Detail(trip, List.of(), List.of(), null, Optional.empty(), Optional.empty());
        }
        long tripId = trip.get().id();
        return new Detail(trip, expenses.listByTrip(tripId), fuel.listByTrip(tripId),
                advances.balanceForTrip(tripId), deliveries.findByTrip(tripId),
                invoices.findByRequest(request.id()));
    }

    private List<Node> render(Detail detail) {
        VBox story = new VBox(14,
                section("Solicitud", requestGrid()),
                section("Viaje", tripGrid(detail)),
                section("Costos", costGrid(detail)),
                section("Entrega", deliveryGrid(detail.delivery())),
                section("Factura", invoiceGrid(detail.invoice())));
        return story.getChildren();
    }

    private Node requestGrid() {
        VBox box = new VBox(6,
                kv("Folio", request.folio()),
                kv("Cliente", request.clientName()),
                kv("Ruta", request.routeLabel()),
                kv("Mercancia", request.cargoDescription()),
                kv("Peso aproximado", request.estimatedWeight() == null
                        ? "-" : request.estimatedWeight().toPlainString() + " kg"),
                kv("Recoleccion", Dates.format(request.pickupScheduled())),
                kv("Entrega", Dates.format(request.deliveryScheduled())),
                kv("Tarifa acordada", request.agreedRate() == null
                        ? "Sin autorizar" : Money.format(request.agreedRate())),
                kv("Documentacion", request.requiresDocuments() ? "Requerida" : "No requerida"),
                kv("Observaciones", request.notes() == null || request.notes().isBlank()
                        ? "-" : request.notes()),
                kvNode("Estado", StatusBadge.of(request.status().label(),
                        StatusTones.request(request.status()))));
        return box;
    }

    private Node tripGrid(Detail detail) {
        if (detail.trip().isEmpty()) {
            return Ui.muted("Sin viaje asignado");
        }
        Trip trip = detail.trip().get();
        return new VBox(6,
                kvNode("Estado", StatusBadge.of(trip.status().label(), StatusTones.trip(trip.status()))),
                kv("Unidad", trip.vehicleLabel()),
                kv("Operador", trip.employeeName()),
                kv("Periodo", Dates.format(trip.plannedStart()) + " a " + Dates.format(trip.plannedEnd())),
                kv("Salida", Dates.format(trip.departure())),
                kv("Llegada", Dates.format(trip.arrival())),
                kv("Km reales", trip.actualKm() == null ? "-" : trip.actualKm().toPlainString()));
    }

    private Node costGrid(Detail detail) {
        BigDecimal spent = detail.expenses().stream()
                .map(Expense::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fuelTotal = detail.fuelLoads().stream()
                .map(FuelLoad::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = spent.add(fuelTotal);
        return new VBox(6,
                kv("Gastos", detail.expenses().isEmpty() ? "Sin gastos" : Money.format(spent)),
                kv("Combustible", detail.fuelLoads().isEmpty() ? "Sin cargas" : Money.format(fuelTotal)),
                kv("Costo total", Money.format(total)),
                kv("Anticipo", detail.advance() == null ? "-" : detail.advance().label()));
    }

    private Node deliveryGrid(Optional<Delivery> delivery) {
        if (delivery.isEmpty()) {
            return Ui.muted("Sin entrega registrada");
        }
        Delivery record = delivery.get();
        return new VBox(6,
                kv("Fecha", Dates.format(record.actualDatetime())),
                kv("Recibio", record.receivedBy()),
                kv("Evidencia", record.evidenceReference()),
                kv("Estado", record.status().label()));
    }

    private Node invoiceGrid(Optional<Invoice> invoice) {
        if (invoice.isEmpty()) {
            return Ui.muted("Sin factura");
        }
        Invoice record = invoice.get();
        return new VBox(6,
                kv("No.", record.invoiceNumber()),
                kv("Importe", Money.format(record.amount())),
                kv("Pagado", Money.format(record.paid())),
                kv("Saldo", Money.format(record.balance())),
                kvNode("Estado", StatusBadge.of(record.status().label(),
                        StatusTones.invoice(record.status()))));
    }

    private Node section(String title, Node content) {
        VBox box = new VBox(8, new Label(title), new Separator(), content);
        box.getStyleClass().add("card");
        ((Label) box.getChildren().get(0)).getStyleClass().add("section-title");
        return box;
    }

    private Node kv(String key, String value) {
        return kvNode(key, new Label(value == null || value.isBlank() ? "-" : value));
    }

    private Node kvNode(String key, Node value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("kpi-label");
        keyLabel.setMinWidth(150);
        keyLabel.setPrefWidth(150);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(8, keyLabel, spacer, value);
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return row;
    }
}
