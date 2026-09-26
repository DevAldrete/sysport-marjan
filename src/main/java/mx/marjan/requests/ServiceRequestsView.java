package mx.marjan.requests;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import mx.marjan.clients.Client;
import mx.marjan.clients.ClientService;
import mx.marjan.fleet.Vehicle;
import mx.marjan.operators.Employee;
import mx.marjan.routes.Route;
import mx.marjan.routes.RouteService;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;
import mx.marjan.trips.Delivery;
import mx.marjan.trips.DeliveryService;
import mx.marjan.trips.Trip;
import mx.marjan.trips.TripService;
import mx.marjan.finance.AdvanceService;
import mx.marjan.finance.ExpenseService;
import mx.marjan.finance.Invoice;
import mx.marjan.finance.InvoiceService;

public class ServiceRequestsView extends BaseView {

    private final ServiceRequestService service = new ServiceRequestService();
    private final ClientService clientService = new ClientService();
    private final RouteService routeService = new RouteService();
    private final TripService tripService = new TripService();
    private final ExpenseService expenseService = new ExpenseService();
    private final AdvanceService advanceService = new AdvanceService();
    private final DeliveryService deliveryService = new DeliveryService();
    private final InvoiceService invoiceService = new InvoiceService();

    private final RecordTableModel<ServiceRequest> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Folio", ServiceRequest::folio),
            RecordTableModel.Column.of("Cliente", ServiceRequest::clientName),
            RecordTableModel.Column.of("Ruta", ServiceRequest::routeLabel),
            RecordTableModel.Column.of("Peso", ServiceRequest::estimatedWeight),
            RecordTableModel.Column.of("Recoleccion", request -> Dates.format(request.pickupScheduled())),
            RecordTableModel.Column.of("Entrega", request -> Dates.format(request.deliveryScheduled())),
            RecordTableModel.Column.of("Tarifa", request -> request.agreedRate() == null
                    ? "" : Money.format(request.agreedRate())),
            RecordTableModel.Column.of("Estado", request -> request.status().label())));
    private final JTable table = Ui.table(model);

    private final JTextField folioField = new JTextField(10);
    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final javax.swing.JComboBox<Object> clientFilter = new javax.swing.JComboBox<>();
    private final javax.swing.JComboBox<Object> statusFilter = new javax.swing.JComboBox<>();

    public ServiceRequestsView() {
        add(buildFilters(), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reloadClients();
        reload();
        // Keep the lifecycle moving while the screen is open (BR-03 automation).
        new javax.swing.Timer(60_000, event -> sweep()).start();
    }

    private void sweep() {
        mx.marjan.shared.Async.run(() -> tripService.sweepLifecycle(),
                changes -> {
                    if (changes > 0) {
                        reload();
                    }
                },
                failure -> System.err.println("Lifecycle sweep failed: " + failure.getMessage()));
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
        JPanel actions = Ui.row(
                Ui.button("Nueva", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Autorizar", this::openAuthorize),
                Ui.button("Programar", this::openSchedule),
                Ui.button("Asignar viaje", this::openAssign),
                Ui.button("Cancelar", this::openCancel),
                Ui.button("Cerrar", this::closeRequest),
                Ui.button("Detalle", this::openDetail),
                Ui.button("Eliminar", this::deleteRequest));
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
        LocalDate from = fromField.getText().isBlank() ? null
                : Dates.parseDate(fromField.getText()).orElse(null);
        LocalDate to = toField.getText().isBlank() ? null
                : Dates.parseDate(toField.getText()).orElse(null);
        Object client = clientFilter.getSelectedItem();
        Object status = statusFilter.getSelectedItem();
        RequestFilter filter = new RequestFilter(
                folioField.getText(),
                client instanceof Client c ? c.id() : null,
                status instanceof RequestStatus s ? s : null,
                from, to);
        load(() -> {
            tripService.sweepLifecycle();
            return service.search(filter);
        }, model::setRows);
    }

    private ServiceRequest selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void openNew() {
        mx.marjan.shared.Async.run(
                () -> new Object[] { clientService.listActive(), routeService.listAll() },
                data -> {
                    @SuppressWarnings("unchecked")
                    List<Client> clients = (List<Client>) data[0];
                    @SuppressWarnings("unchecked")
                    List<Route> routes = (List<Route>) data[1];
                    if (clients.isEmpty() || routes.isEmpty()) {
                        Ui.info(this, "Se necesitan al menos un cliente y una ruta");
                        return;
                    }
                    showNewForm(clients, routes);
                },
                failure -> Ui.failure(this, failure));
    }

    private void showNewForm(List<Client> clients, List<Route> routes) {
        FormPanel form = new FormPanel()
                .addCombo("client", "Cliente", clients.toArray(), clients.get(0))
                .addCombo("route", "Ruta", routes.toArray(), routes.get(0))
                .addText("cargo", "Descripcion de la mercancia", "")
                .addText("weight", "Peso aproximado (kg)", "0")
                .addText("pickup", "Recoleccion (yyyy-MM-dd HH:mm)", "")
                .addText("delivery", "Entrega (yyyy-MM-dd HH:mm)", "")
                .addCheck("documents", "Requiere documentacion", true)
                .addArea("notes", "Observaciones", "");
        ModalForm.show(this, "Nueva solicitud", form, () -> {
            Result<BigDecimal> weightResult = Money.require(form.text("weight"), "peso aproximado");
            if (weightResult.isErr()) {
                return weightResult;
            }
            BigDecimal weight = weightResult.value();
            LocalDateTime pickup = optionalDateTime(form.text("pickup"));
            LocalDateTime delivery = optionalDateTime(form.text("delivery"));
            if (!form.text("pickup").isBlank() && pickup == null
                    || !form.text("delivery").isBlank() && delivery == null) {
                return Result.err("Las fechas deben tener el formato yyyy-MM-dd HH:mm");
            }
            Client client = (Client) form.selected("client");
            Route route = (Route) form.selected("route");
            ServiceRequest draft = new ServiceRequest(0, "", client.id(), client.name(), route.id(),
                    route.label(), form.text("cargo"), weight, pickup, delivery, null,
                    form.checked("documents"), RequestStatus.REQUESTED, form.text("notes"),
                    LocalDateTime.now());
            return service.create(draft);
        }, this::reload);
    }

    private void openEdit() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("cargo", "Descripcion de la mercancia", request.cargoDescription())
                .addText("weight", "Peso aproximado (kg)",
                        request.estimatedWeight() == null ? "0" : request.estimatedWeight().toPlainString())
                .addCheck("documents", "Requiere documentacion", request.requiresDocuments())
                .addArea("notes", "Observaciones", request.notes());
        ModalForm.show(this, "Editar solicitud " + request.folio(), form, () -> {
            Result<BigDecimal> weightResult = Money.require(form.text("weight"), "peso aproximado");
            if (weightResult.isErr()) {
                return weightResult;
            }
            BigDecimal weight = weightResult.value();
            ServiceRequest updated = new ServiceRequest(request.id(), request.folio(),
                    request.clientId(), request.clientName(), request.routeId(), request.routeLabel(),
                    form.text("cargo"), weight, request.pickupScheduled(), request.deliveryScheduled(),
                    request.agreedRate(), form.checked("documents"), request.status(),
                    form.text("notes"), request.createdAt());
            return service.update(updated);
        }, this::reload);
    }

    private void openAuthorize() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        mx.marjan.shared.Async.run(
                () -> request.agreedRate() != null ? request.agreedRate()
                        : clientService.suggestRate(request.clientId(), request.routeId(), Dates.today())
                                .orElse(BigDecimal.ZERO),
                suggested -> {
                    FormPanel form = new FormPanel()
                            .addText("rate", "Tarifa acordada", suggested.toPlainString());
                    ModalForm.show(this, "Autorizar " + request.folio(), form, () -> {
                        Result<BigDecimal> rateResult = Money.require(form.text("rate"), "tarifa acordada");
                        return rateResult.isErr() ? rateResult
                                : service.authorize(request.id(), rateResult.value());
                    }, this::reload);
                },
                failure -> Ui.failure(this, failure));
    }

    private void openSchedule() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("pickup", "Recoleccion (yyyy-MM-dd HH:mm)", Dates.format(request.pickupScheduled()))
                .addText("delivery", "Entrega (yyyy-MM-dd HH:mm)", Dates.format(request.deliveryScheduled()));
        ModalForm.show(this, "Programar " + request.folio(), form, () -> {
            LocalDateTime pickup = Dates.parseDateTime(form.text("pickup")).orElse(null);
            LocalDateTime delivery = Dates.parseDateTime(form.text("delivery")).orElse(null);
            return service.schedule(request.id(), pickup, delivery);
        }, this::reload);
    }

    private void openAssign() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        if (request.pickupScheduled() == null || request.deliveryScheduled() == null) {
            Ui.info(this, "La solicitud debe estar programada");
            return;
        }
        mx.marjan.shared.Async.run(
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
        ModalForm.show(this, "Asignar viaje a " + request.folio(), form, () -> {
            Vehicle vehicle = (Vehicle) form.selected("vehicle");
            Employee operator = (Employee) form.selected("operator");
            return tripService.assign(request.id(), vehicle.id(), operator.id());
        }, this::reload);
    }

    private void openCancel() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        FormPanel form = new FormPanel().addArea("reason", "Motivo", "");
        ModalForm.show(this, "Cancelar " + request.folio(), form,
                () -> service.cancel(request.id(), form.text("reason")), this::reload);
    }

    private void closeRequest() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        mx.marjan.shared.Async.run(() -> tripService.closeRequest(request.id()),
                result -> {
                    if (result.isErr()) {
                        Ui.error(this, "No se puede cerrar", result.problems());
                    } else {
                        Ui.info(this, "Solicitud cerrada");
                        reload();
                    }
                },
                failure -> Ui.failure(this, failure));
    }

    private void deleteRequest() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        Ui.delete(this, "la solicitud " + request.folio()
                        + " y todo lo relacionado (viaje, gastos, anticipos, incidencias, entrega, factura y pagos)",
                () -> service.delete(request.id()), this::reload);
    }

    private void openDetail() {
        ServiceRequest request = selected();
        if (request == null) {
            Ui.info(this, "Seleccione una solicitud");
            return;
        }
        mx.marjan.shared.Async.run(() -> {
            StringBuilder text = new StringBuilder();
            text.append("Folio: ").append(request.folio()).append('\n')
                    .append("Cliente: ").append(request.clientName()).append('\n')
                    .append("Ruta: ").append(request.routeLabel()).append('\n')
                    .append("Estado: ").append(request.status().label()).append('\n')
                    .append("Tarifa: ").append(request.agreedRate() == null ? "-" : Money.format(request.agreedRate()))
                    .append("\n\n");
            tripService.findByRequest(request.id()).ifPresent(trip -> appendTrip(text, trip));
            return text.toString();
        }, text -> showDetailDialog(request, text), failure -> Ui.failure(this, failure));
    }

    private void showDetailDialog(ServiceRequest request, String text) {
        javax.swing.JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Detalle " + request.folio(), Dialog.ModalityType.APPLICATION_MODAL);
        JTextArea area = new JTextArea(text, 18, 60);
        area.setEditable(false);
        dialog.add(Ui.scroll(area), BorderLayout.CENTER);
        dialog.add(Ui.row(Ui.button("Cerrar", dialog::dispose)), BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void appendTrip(StringBuilder text, Trip trip) {
        text.append("--- Viaje ---\n")
                .append("Unidad: ").append(trip.vehicleLabel()).append('\n')
                .append("Operador: ").append(trip.employeeName()).append('\n')
                .append("Estado: ").append(trip.status().label()).append('\n')
                .append("Salida: ").append(Dates.format(trip.departure())).append('\n')
                .append("Llegada: ").append(Dates.format(trip.arrival())).append('\n')
                .append("Km reales: ").append(trip.actualKm() == null ? "-" : trip.actualKm()).append('\n');
        BigDecimal expenses = expenseService.listByTrip(trip.id()).stream()
                .map(expense -> expense.amount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        text.append("Gastos: ").append(Money.format(expenses)).append('\n')
                .append("Anticipo: ").append(advanceService.balanceForTrip(trip.id()).label()).append('\n');
        deliveryService.findByTrip(trip.id()).ifPresent(delivery -> appendDelivery(text, delivery));
        invoiceService.findByRequest(trip.serviceRequestId())
                .ifPresent(invoice -> appendInvoice(text, invoice));
    }

    private void appendDelivery(StringBuilder text, Delivery delivery) {
        text.append("--- Entrega ---\n")
                .append("Fecha: ").append(Dates.format(delivery.actualDatetime())).append('\n')
                .append("Recibio: ").append(delivery.receivedBy()).append('\n')
                .append("Evidencia: ").append(delivery.evidenceReference()).append('\n')
                .append("Estado: ").append(delivery.status().label()).append('\n');
    }

    private void appendInvoice(StringBuilder text, Invoice invoice) {
        text.append("--- Factura ---\n")
                .append("No.: ").append(invoice.invoiceNumber()).append('\n')
                .append("Importe: ").append(Money.format(invoice.amount())).append('\n')
                .append("Pagado: ").append(Money.format(invoice.paid())).append('\n')
                .append("Saldo: ").append(Money.format(invoice.balance())).append('\n')
                .append("Estado: ").append(invoice.status().label()).append('\n');
    }

    private LocalDateTime optionalDateTime(String text) {
        return text == null || text.isBlank() ? null : Dates.parseDateTime(text).orElse(null);
    }
}
