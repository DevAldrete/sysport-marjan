package mx.marjan.requests;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import mx.marjan.clients.Client;
import mx.marjan.clients.ClientService;
import mx.marjan.fleet.Vehicle;
import mx.marjan.operators.Employee;
import mx.marjan.routes.Route;
import mx.marjan.routes.RouteService;
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.trips.TripService;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.Icons;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.StatusTones;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class ServiceRequestsView extends BaseView {

    private final ServiceRequestService service = new ServiceRequestService();
    private final ClientService clientService = new ClientService();
    private final RouteService routeService = new RouteService();
    private final TripService tripService = new TripService();

    private final RecordTable<ServiceRequest> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Folio", ServiceRequest::folio),
            RecordTable.Column.of("Cliente", ServiceRequest::clientName),
            RecordTable.Column.text("Ruta", ServiceRequest::routeLabel, 40),
            RecordTable.Column.number("Peso", ServiceRequest::estimatedWeight),
            RecordTable.Column.of("Recoleccion", request -> Dates.format(request.pickupScheduled())),
            RecordTable.Column.of("Entrega", request -> Dates.format(request.deliveryScheduled())),
            RecordTable.Column.money("Tarifa", ServiceRequest::agreedRate),
            RecordTable.Column.badge("Estado", request -> request.status().label(),
                    request -> StatusTones.request(request.status()))));

    private final TextField folioField = new TextField();
    private final TextField fromField = new TextField();
    private final TextField toField = new TextField();
    private final ComboBox<Object> clientFilter = new ComboBox<>();
    private final ComboBox<Object> statusFilter = new ComboBox<>();

    public ServiceRequestsView() {
        folioField.setPromptText("Folio");
        fromField.setPromptText("AAAA-MM-DD");
        toField.setPromptText("AAAA-MM-DD");
        clientFilter.getItems().add("(todos)");
        clientFilter.setValue("(todos)");
        statusFilter.getItems().add("(todos)");
        for (RequestStatus status : RequestStatus.values()) {
            statusFilter.getItems().add(status);
        }
        statusFilter.setValue("(todos)");
        folioField.setOnAction(event -> reload());
        fromField.setOnAction(event -> reload());
        toField.setOnAction(event -> reload());
        Ui.onDoubleClick(table, request -> openDetail());

        var filters = Ui.filters(new Label("Folio:"), folioField,
                new Label("Cliente:"), clientFilter,
                new Label("Estado:"), statusFilter,
                new Label("Desde:"), fromField,
                new Label("Hasta:"), toField,
                Ui.button("Buscar", this::reload),
                Ui.button("Limpiar", this::clearFilters));
        var nueva = Ui.primary("Nueva", this::openNew);
        nueva.setGraphic(Icons.action(Feather.PLUS));
        var actions = Ui.toolbar(nueva,
                Ui.button("Detalle", this::openDetail),
                Ui.button("Editar", this::openEdit),
                Ui.button("Autorizar", this::openAuthorize),
                Ui.button("Programar", this::openSchedule),
                Ui.button("Asignar viaje", this::openAssign),
                Ui.button("Cerrar", this::closeRequest),
                Ui.button("Cancelar", this::openCancel),
                Ui.button("Eliminar", this::deleteRequest),
                Ui.button("Recargar", this::reload));
        setTop(new VBox(4, filters, actions));
        setCenter(table);

        reloadClients();
        reload();
        // Keep the lifecycle moving while the screen is open (BR-03 automation).
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(60), event -> sweep()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    private void sweep() {
        Async.run(() -> tripService.sweepLifecycle(), changes -> {
            if (changes > 0) {
                reload();
            }
        }, failure -> setStatus("No se pudo actualizar el ciclo de vida de las solicitudes"));
    }

    private void reloadClients() {
        load(clientService::listActive, clients -> {
            Object selected = clientFilter.getValue();
            clientFilter.getItems().setAll("(todos)");
            clientFilter.getItems().addAll(clients);
            if (selected != null && clientFilter.getItems().contains(selected)) {
                clientFilter.setValue(selected);
            }
        });
    }

    private void clearFilters() {
        folioField.setText("");
        fromField.setText("");
        toField.setText("");
        clientFilter.setValue("(todos)");
        statusFilter.setValue("(todos)");
        reload();
    }

    @Override
    public void reload() {
        LocalDate from = fromField.getText().isBlank() ? null
                : Dates.parseDate(fromField.getText()).orElse(null);
        LocalDate to = toField.getText().isBlank() ? null
                : Dates.parseDate(toField.getText()).orElse(null);
        Object client = clientFilter.getValue();
        Object status = statusFilter.getValue();
        RequestFilter filter = new RequestFilter(
                folioField.getText(),
                client instanceof Client c ? c.id() : null,
                status instanceof RequestStatus s ? s : null,
                from, to);
        loadRows(() -> {
            tripService.sweepLifecycle();
            return service.search(filter);
        }, table::setRows);
    }

    private ServiceRequest requireSelection() {
        ServiceRequest request = table.selected();
        if (request == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una solicitud");
        }
        return request;
    }

    private void openNew() {
        Async.run(() -> new Object[] { clientService.listActive(), routeService.listAll() }, data -> {
            @SuppressWarnings("unchecked")
            List<Client> clients = (List<Client>) data[0];
            @SuppressWarnings("unchecked")
            List<Route> routes = (List<Route>) data[1];
            if (clients.isEmpty() || routes.isEmpty()) {
                Ui.info(Ui.windowOf(this), "Se necesitan al menos un cliente y una ruta");
                return;
            }
            showNewForm(clients, routes);
        }, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void showNewForm(List<Client> clients, List<Route> routes) {
        FormPanel form = new FormPanel()
                .addCombo("client", "Cliente", clients.toArray(), clients.get(0))
                .addCombo("route", "Ruta", routes.toArray(), routes.get(0))
                .addText("cargo", "Descripcion de la mercancia", "", "Que se va a transportar")
                .addText("weight", "Peso aproximado (kg)", "0", "En kilogramos, ej. 1200")
                .addText("pickup", "Recoleccion (opcional)", "", "Formato: AAAA-MM-DD HH:MM")
                .addText("delivery", "Entrega (opcional)", "", "Formato: AAAA-MM-DD HH:MM")
                .addCheck("documents", "Requiere documentacion", true)
                .addArea("notes", "Observaciones", "", "Notas internas (opcional)");
        form.validate("weight", Validators.number());
        form.validate("pickup", Validators.dateTime());
        form.validate("delivery", Validators.dateTime());
        ModalForm.show(Ui.windowOf(this), "Nueva solicitud", form, () -> {
            Result<BigDecimal> weightResult = Money.require(form.text("weight"), "peso aproximado");
            if (weightResult.isErr()) {
                return weightResult;
            }
            LocalDateTime pickup = optionalDateTime(form.text("pickup"));
            LocalDateTime delivery = optionalDateTime(form.text("delivery"));
            if (!form.text("pickup").isBlank() && pickup == null
                    || !form.text("delivery").isBlank() && delivery == null) {
                return Result.err("Las fechas deben tener el formato AAAA-MM-DD HH:MM");
            }
            Client client = (Client) form.selected("client");
            Route route = (Route) form.selected("route");
            ServiceRequest draft = new ServiceRequest(0, "", client.id(), client.name(), route.id(),
                    route.label(), form.text("cargo"), weightResult.value(), 0, null, pickup, delivery, null,
                    form.checked("documents"), RequestStatus.REQUESTED, form.text("notes"),
                    LocalDateTime.now());
            return service.create(draft);
        }, this::reload);
    }

    private void openEdit() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        FormPanel form = new FormPanel()
                .addText("cargo", "Descripcion de la mercancia", request.cargoDescription(),
                        "Que se va a transportar")
                .addText("weight", "Peso aproximado (kg)",
                        request.estimatedWeight() == null ? "0" : request.estimatedWeight().toPlainString(),
                        "En kilogramos, ej. 1200")
                .addCheck("documents", "Requiere documentacion", request.requiresDocuments())
                .addArea("notes", "Observaciones", request.notes(), "Notas internas (opcional)");
        form.validate("weight", Validators.number());
        ModalForm.show(Ui.windowOf(this), "Editar solicitud " + request.folio(), form, () -> {
            Result<BigDecimal> weightResult = Money.require(form.text("weight"), "peso aproximado");
            if (weightResult.isErr()) {
                return weightResult;
            }
            ServiceRequest updated = new ServiceRequest(request.id(), request.folio(),
                    request.clientId(), request.clientName(), request.routeId(), request.routeLabel(),
                    form.text("cargo"), weightResult.value(), request.packageCount(),
                    request.packageWeight(), request.pickupScheduled(),
                    request.deliveryScheduled(), request.agreedRate(), form.checked("documents"),
                    request.status(), form.text("notes"), request.createdAt());
            return service.update(updated);
        }, this::reload);
    }

    private void openAuthorize() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        Async.run(() -> request.agreedRate() != null ? request.agreedRate()
                        : clientService.suggestRate(request.clientId(), request.routeId(), Dates.today())
                                .orElse(BigDecimal.ZERO),
                suggested -> {
                    FormPanel form = new FormPanel()
                            .addText("rate", "Tarifa acordada", suggested.toPlainString(),
                                    "Importe sin IVA; se propone la tarifa del cliente")
                            .validate("rate", Validators.money());
                    ModalForm.show(Ui.windowOf(this), "Autorizar " + request.folio(), form, () -> {
                        Result<BigDecimal> rateResult = Money.require(form.text("rate"), "tarifa acordada");
                        return rateResult.isErr() ? rateResult
                                : service.authorize(request.id(), rateResult.value());
                    }, this::reload);
                },
                failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void openSchedule() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        FormPanel form = new FormPanel()
                .addText("pickup", "Recoleccion", Dates.format(request.pickupScheduled()),
                        "Formato: AAAA-MM-DD HH:MM")
                .addText("delivery", "Entrega", Dates.format(request.deliveryScheduled()),
                        "Formato: AAAA-MM-DD HH:MM");
        form.validate("pickup", Validators.dateTime());
        form.validate("delivery", Validators.dateTime());
        ModalForm.show(Ui.windowOf(this), "Programar " + request.folio(), form, () -> {
            LocalDateTime pickup = Dates.parseDateTime(form.text("pickup")).orElse(null);
            LocalDateTime delivery = Dates.parseDateTime(form.text("delivery")).orElse(null);
            return service.schedule(request.id(), pickup, delivery);
        }, this::reload);
    }

    private void openAssign() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        if (request.pickupScheduled() == null || request.deliveryScheduled() == null) {
            Ui.info(Ui.windowOf(this), "La solicitud debe estar programada");
            return;
        }
        Async.run(
                () -> new Object[] {
                        tripService.eligibleVehicles(request.pickupScheduled(), request.deliveryScheduled()),
                        tripService.eligibleOperators(request.pickupScheduled(), request.deliveryScheduled()) },
                data -> {
                    @SuppressWarnings("unchecked")
                    List<Vehicle> vehicles = (List<Vehicle>) data[0];
                    @SuppressWarnings("unchecked")
                    List<Employee> operators = (List<Employee>) data[1];
                    showAssignDialog(request, vehicles, operators);
                },
                failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void showAssignDialog(ServiceRequest request, List<Vehicle> vehicles, List<Employee> operators) {
        if (vehicles.isEmpty() || operators.isEmpty()) {
            Ui.info(Ui.windowOf(this), "No hay unidades u operadores elegibles para el periodo "
                    + Dates.format(request.pickupScheduled()) + " - " + Dates.format(request.deliveryScheduled()));
            return;
        }
        FormPanel form = new FormPanel()
                .addText("window", "Periodo", Dates.format(request.pickupScheduled())
                        + " a " + Dates.format(request.deliveryScheduled()))
                .addCombo("vehicle", "Unidad", vehicles.toArray(), vehicles.get(0))
                .addCombo("operator", "Operador", operators.toArray(), operators.get(0));
        form.control("window").setDisable(true);
        ModalForm.show(Ui.windowOf(this), "Asignar viaje a " + request.folio(), form, () -> {
            Vehicle vehicle = (Vehicle) form.selected("vehicle");
            Employee operator = (Employee) form.selected("operator");
            return tripService.assign(request.id(), vehicle.id(), operator.id());
        }, this::reload);
    }

    private void openCancel() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        FormPanel form = new FormPanel().addArea("reason", "Motivo", "", "Razon de la cancelacion");
        ModalForm.show(Ui.windowOf(this), "Cancelar " + request.folio(), form,
                () -> service.cancel(request.id(), form.text("reason")), this::reload);
    }

    private void closeRequest() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        Async.run(() -> tripService.closeRequest(request.id()), result -> {
            if (result.isErr()) {
                Ui.error(Ui.windowOf(this), "No se puede cerrar", result.problems());
            } else {
                Ui.success(Ui.windowOf(this), "Solicitud cerrada");
                reload();
            }
        }, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void deleteRequest() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        Ui.delete(Ui.windowOf(this), "la solicitud " + request.folio()
                        + " y todo lo relacionado (viaje, gastos, anticipos, incidencias, entrega, factura y pagos)",
                () -> service.delete(request.id()), this::reload);
    }

    private void openDetail() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        ServiceRequestDetailDialog.show(Ui.windowOf(this), request);
    }

    private LocalDateTime optionalDateTime(String text) {
        return text == null || text.isBlank() ? null : Dates.parseDateTime(text).orElse(null);
    }
}
