package mx.marjan.requests;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import mx.marjan.clients.Client;
import mx.marjan.clients.ClientService;
import mx.marjan.fleet.Vehicle;
import mx.marjan.operators.Employee;
import mx.marjan.routes.Route;
import mx.marjan.routes.RouteService;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.Async;
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
import mx.marjan.trips.TripService;

public class ServiceRequestsView extends BaseView {

    private final ServiceRequestService service = new ServiceRequestService();
    private final CargoPackageService packageService = new CargoPackageService();
    private final ClientService clientService = new ClientService();
    private final RouteService routeService = new RouteService();
    private final TripService tripService = new TripService();

    private final RecordTableModel<ServiceRequest> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Folio", ServiceRequest::folio),
            RecordTableModel.Column.of("Cliente", ServiceRequest::clientName),
            RecordTableModel.Column.of("Ruta", ServiceRequest::routeLabel),
            RecordTableModel.Column.of("Paquetes", request -> request.packageCount() == 0 ? "" : request.packageCount()),
            RecordTableModel.Column.of("Peso", ServiceRequest::effectiveWeight),
            RecordTableModel.Column.of("Recoleccion", request -> Dates.format(request.pickupScheduled())),
            RecordTableModel.Column.of("Entrega", request -> Dates.format(request.deliveryScheduled())),
            RecordTableModel.Column.of("Tarifa", request -> request.agreedRate() == null
                    ? "" : Money.format(request.agreedRate())),
            RecordTableModel.Column.of("Estado", request -> request.status().label())));
    private final JTable table = Ui.table(model);

    private final JTextField folioField = new JTextField(10);
    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final JComboBox<Object> clientFilter = new JComboBox<>();
    private final JComboBox<Object> statusFilter = new JComboBox<>();
    // Keep the lifecycle moving while the screen is open (BR-03 automation).
    private final javax.swing.Timer sweepTimer = new javax.swing.Timer(60_000, event -> sweep());

    public ServiceRequestsView() {
        Ui.onEnter(folioField, this::reload);
        Ui.onDoubleClick(table, this::openDetail);
        add(buildFilters(), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reloadClients();
        reload();
        sweepTimer.start();
    }

    @Override
    public void removeNotify() {
        sweepTimer.stop();
        super.removeNotify();
    }

    private void sweep() {
        Async.run(() -> tripService.sweepLifecycle(), changes -> {
            if (changes > 0) {
                reload();
            }
        }, failure -> setStatus("No se pudo actualizar el ciclo de vida de las solicitudes"));
    }

    private JPanel buildFilters() {
        statusFilter.addItem("(todos)");
        for (RequestStatus status : RequestStatus.values()) {
            statusFilter.addItem(status);
        }
        JPanel filters = Ui.row(new JLabel("Folio:"), folioField,
                new JLabel("Cliente:"), clientFilter,
                new JLabel("Estado:"), statusFilter,
                new JLabel("Desde:"), fromField,
                new JLabel("Hasta:"), toField,
                Ui.button("Buscar", this::reload),
                Ui.button("Limpiar", this::clearFilters),
                Ui.button("Recargar", this::reload));
        boolean canWrite = Session.has(Permissions.REQUESTS_WRITE);
        boolean canAssign = Session.has(Permissions.REQUESTS_ASSIGN);
        JPanel actions = Ui.row(
                Ui.button("Nueva", "Registrar una solicitud de servicio", this::openNew, canWrite),
                Ui.button("Editar", "Editar los datos de la solicitud", this::openEdit, canWrite),
                Ui.button("Autorizar", "Definir la tarifa acordada", this::openAuthorize, canWrite),
                Ui.button("Programar", "Definir recoleccion y entrega", this::openSchedule, canWrite),
                Ui.button("Asignar viaje", "Elegir unidad y operador", this::openAssign, canAssign),
                Ui.button("Cancelar", "Cancelar la solicitud", this::openCancel, canWrite),
                Ui.button("Cerrar", "Cerrar la solicitud entregada", this::closeRequest, canWrite),
                Ui.button("Detalle", "Ver la historia completa de la solicitud", this::openDetail),
                Ui.button("Eliminar", "Eliminar la solicitud y todo lo relacionado", this::deleteRequest, canWrite));
        return Ui.column(filters, actions);
    }

    private void reloadClients() {
        load(clientService::listActive, clients -> {
            Object selected = clientFilter.getSelectedItem();
            clientFilter.removeAllItems();
            clientFilter.addItem("(todos)");
            for (Client client : clients) {
                clientFilter.addItem(client);
            }
            if (selected != null) {
                clientFilter.setSelectedItem(selected);
            }
        });
    }

    private void clearFilters() {
        folioField.setText("");
        fromField.setText("");
        toField.setText("");
        clientFilter.setSelectedIndex(0);
        statusFilter.setSelectedIndex(0);
        reload();
    }

    @Override
    public void reload() {
        LocalDate from = null;
        LocalDate to = null;
        if (!fromField.getText().isBlank()) {
            from = Dates.parseDate(fromField.getText()).orElse(null);
            if (from == null) {
                setStatus("La fecha 'Desde' no es valida (use AAAA-MM-DD)");
                return;
            }
        }
        if (!toField.getText().isBlank()) {
            to = Dates.parseDate(toField.getText()).orElse(null);
            if (to == null) {
                setStatus("La fecha 'Hasta' no es valida (use AAAA-MM-DD)");
                return;
            }
        }
        Object client = clientFilter.getSelectedItem();
        Object status = statusFilter.getSelectedItem();
        RequestFilter filter = new RequestFilter(
                folioField.getText(),
                client instanceof Client c ? c.id() : null,
                status instanceof RequestStatus s ? s : null,
                from, to);
        loadRows(() -> {
            tripService.sweepLifecycle();
            return service.search(filter);
        }, model::setRows);
    }

    private ServiceRequest selected() {
        return selectedRow(table, model);
    }

    private ServiceRequest requireSelection() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
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
                Ui.info(this, "Se necesitan al menos un cliente y una ruta");
                return;
            }
            showNewForm(clients, routes);
        }, failure -> Ui.failure(this, failure));
    }

    private void showNewForm(List<Client> clients, List<Route> routes) {
        PackageEditorPanel packages = new PackageEditorPanel(0, List.of());
        FormPanel form = new FormPanel()
                .addCombo("client", "Cliente", clients.toArray(), clients.get(0))
                .addCombo("route", "Ruta", routes.toArray(), routes.get(0))
                .addText("cargo", "Descripcion de la mercancia", "", "Resumen; el detalle va en los paquetes")
                .addText("weight", "Peso manual (kg)", "0", "Solo si no captura paquetes")
                .addText("pickup", "Recoleccion (opcional)", "", "Formato: AAAA-MM-DD HH:MM")
                .addText("delivery", "Entrega (opcional)", "", "Formato: AAAA-MM-DD HH:MM")
                .addCheck("documents", "Requiere documentacion", true)
                .addArea("notes", "Observaciones", "", "Notas internas (opcional)")
                .addComputed("total", "Peso total (calculado)", () -> weightLabel(packages.totalWeight()));
        form.validate("weight", Validators.number());
        form.validate("pickup", Validators.dateTime());
        form.validate("delivery", Validators.dateTime());
        packages.setOnChange(form::refresh);
        form.addSection(packages);
        ModalForm.show(this, "Nueva solicitud", form, () -> {
            Result<BigDecimal> weightResult = parseWeight(form);
            if (weightResult.isErr()) {
                return weightResult;
            }
            BigDecimal weight = weightResult.value();
            LocalDateTime pickup = optionalDateTime(form.text("pickup"));
            LocalDateTime delivery = optionalDateTime(form.text("delivery"));
            if (!form.text("pickup").isBlank() && pickup == null
                    || !form.text("delivery").isBlank() && delivery == null) {
                return Result.err("Las fechas deben tener el formato AAAA-MM-DD HH:MM");
            }
            Client client = (Client) form.selected("client");
            Route route = (Route) form.selected("route");
            ServiceRequest draft = new ServiceRequest(0, "", client.id(), client.name(), route.id(),
                    route.label(), form.text("cargo"), weight, 0, null, pickup, delivery, null,
                    form.checked("documents"), RequestStatus.REQUESTED, form.text("notes"),
                    LocalDateTime.now());
            return service.create(draft, packages.packages());
        }, this::reload);
    }

    private void openEdit() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        Async.run(() -> packageService.list(request.id()),
                packages -> showEditForm(request, packages),
                failure -> Ui.failure(this, failure));
    }

    private void showEditForm(ServiceRequest request, List<CargoPackage> existingPackages) {
        PackageEditorPanel packages = new PackageEditorPanel(request.id(), existingPackages);
        FormPanel form = new FormPanel()
                .addText("cargo", "Descripcion de la mercancia", request.cargoDescription(),
                        "Resumen; el detalle va en los paquetes")
                .addText("weight", "Peso manual (kg)",
                        request.estimatedWeight() == null ? "0" : request.estimatedWeight().toPlainString(),
                        "Solo si no captura paquetes");
        form.validate("weight", Validators.number());
        // The agreed rate exists only after authorization; let it be corrected.
        boolean editRate = request.agreedRate() != null;
        if (editRate) {
            form.addText("rate", "Tarifa acordada", request.agreedRate().toPlainString(),
                    "Importe sin IVA; se corrige aqui si hubo un error");
            form.validate("rate", Validators.money());
        }
        form.addCheck("documents", "Requiere documentacion", request.requiresDocuments())
                .addArea("notes", "Observaciones", request.notes(), "Notas internas (opcional)")
                .addComputed("total", "Peso total (calculado)", () -> weightLabel(packages.totalWeight()));
        packages.setOnChange(form::refresh);
        form.addSection(packages);
        ModalForm.show(this, "Editar solicitud " + request.folio(), form, () -> {
            Result<BigDecimal> weightResult = parseWeight(form);
            if (weightResult.isErr()) {
                return weightResult;
            }
            BigDecimal rate = request.agreedRate();
            if (editRate) {
                Result<BigDecimal> rateResult = Money.require(form.text("rate"), "tarifa acordada");
                if (rateResult.isErr()) {
                    return rateResult;
                }
                rate = rateResult.value();
            }
            ServiceRequest updated = new ServiceRequest(request.id(), request.folio(),
                    request.clientId(), request.clientName(), request.routeId(), request.routeLabel(),
                    form.text("cargo"), weightResult.value(), request.packageCount(),
                    request.packageWeight(), request.pickupScheduled(),
                    request.deliveryScheduled(), rate, form.checked("documents"),
                    request.status(), form.text("notes"), request.createdAt());
            return service.update(updated, packages.packages());
        }, this::reload);
    }

    /** A weight field is a measure (kg), not money: parse it without forcing cents. */
    private static Result<BigDecimal> parseWeight(FormPanel form) {
        BigDecimal weight = Numbers.parseOrZero(form.text("weight"));
        if (weight == null || weight.signum() < 0) {
            return Result.err("El peso manual debe ser un numero mayor o igual a cero");
        }
        return Result.ok(weight);
    }

    private static String weightLabel(BigDecimal weight) {
        return weight == null ? "-" : weight.stripTrailingZeros().toPlainString() + " kg";
    }

    /** Live BR-08 hint in the assign dialog: does the chosen unit fit the cargo? */
    private static String capacityLabel(ServiceRequest request, FormPanel form) {
        Object selected = form.selected("vehicle");
        if (!(selected instanceof Vehicle vehicle)) {
            return "-";
        }
        BigDecimal load = request.effectiveWeight();
        String capacity = vehicle.loadCapacity() == null ? "-" : vehicle.loadCapacity() + " kg";
        if (load == null || vehicle.loadCapacity() == null) {
            return "Capacidad: " + capacity + " - Carga: " + weightLabel(load);
        }
        boolean fits = vehicle.loadCapacity().compareTo(load) >= 0;
        return "Capacidad: " + capacity + " - Carga: " + weightLabel(load)
                + (fits ? " (suficiente)" : " (EXCEDE LA CAPACIDAD)");
    }

    private void openAuthorize() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        Async.run(
                () -> request.agreedRate() != null ? request.agreedRate()
                        : clientService.suggestRate(request.clientId(), request.routeId(), Dates.today())
                                .orElse(BigDecimal.ZERO),
                suggested -> {
                    FormPanel form = new FormPanel()
                            .addText("rate", "Tarifa acordada", suggested.toPlainString(),
                                    "Importe sin IVA; se propone la tarifa del cliente")
                            .validate("rate", Validators.money());
                    ModalForm.show(this, "Autorizar " + request.folio(), form, () -> {
                        Result<BigDecimal> rateResult = Money.require(form.text("rate"), "tarifa acordada");
                        return rateResult.isErr() ? rateResult
                                : service.authorize(request.id(), rateResult.value());
                    }, this::reload);
                },
                failure -> Ui.failure(this, failure));
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
        ModalForm.show(this, "Programar " + request.folio(), form, () -> {
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
        if (request.status() != RequestStatus.SCHEDULED) {
            Ui.info(this, "La solicitud debe estar programada (autorizada y con fechas) para asignarle un viaje");
            return;
        }
        if (request.pickupScheduled() == null || request.deliveryScheduled() == null) {
            Ui.info(this, "La solicitud debe tener recoleccion y entrega programadas");
            return;
        }
        Async.run(
                () -> new java.util.AbstractMap.SimpleEntry<>(
                        tripService.eligibleVehicles(request.pickupScheduled(), request.deliveryScheduled()),
                        tripService.eligibleOperators(request.pickupScheduled(), request.deliveryScheduled())),
                pair -> showAssignDialog(request, pair.getKey(), pair.getValue()),
                failure -> Ui.failure(this, failure));
    }

    private void showAssignDialog(ServiceRequest request, List<Vehicle> vehicles, List<Employee> operators) {
        if (vehicles.isEmpty() || operators.isEmpty()) {
            Ui.info(this, "No hay unidades u operadores elegibles para el periodo "
                    + Dates.format(request.pickupScheduled()) + " - " + Dates.format(request.deliveryScheduled()));
            return;
        }
        FormPanel form = new FormPanel()
                .addText("window", "Periodo",
                        Dates.format(request.pickupScheduled()) + " a " + Dates.format(request.deliveryScheduled()))
                .addCombo("vehicle", "Unidad", vehicles.toArray(), vehicles.get(0))
                .addCombo("operator", "Operador", operators.toArray(), operators.get(0));
        form.field("window").setEnabled(false);
        form.addComputed("capacity", "Capacidad de la unidad", () -> capacityLabel(request, form));
        form.onSelect("vehicle", form::refresh);
        ModalForm.show(this, "Asignar viaje a " + request.folio(), form, () -> {
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
        ModalForm.show(this, "Cancelar " + request.folio(), form,
                () -> service.cancel(request.id(), form.text("reason")), this::reload);
    }

    private void closeRequest() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        Async.run(() -> tripService.closeRequest(request.id()), result -> {
            if (result.isErr()) {
                Ui.error(this, "No se puede cerrar", result.problems());
            } else {
                Ui.info(this, "Solicitud cerrada");
                reload();
            }
        }, failure -> Ui.failure(this, failure));
    }

    private void deleteRequest() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        Ui.delete(this, "la solicitud " + request.folio()
                        + " y todo lo relacionado (viaje, gastos, anticipos, incidencias, entrega, factura y pagos)",
                () -> service.delete(request.id()), this::reload);
    }

    private void openDetail() {
        ServiceRequest request = requireSelection();
        if (request == null) {
            return;
        }
        ServiceRequestDetailDialog.show(this, request);
    }

    private LocalDateTime optionalDateTime(String text) {
        return text == null || text.isBlank() ? null : Dates.parseDateTime(text).orElse(null);
    }
}
