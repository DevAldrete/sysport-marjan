package mx.marjan.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import mx.marjan.clients.Client;
import mx.marjan.clients.ClientService;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.requests.ServiceRequestService;
import mx.marjan.shared.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.RecordTablePanel;
import mx.marjan.ui.StatusTones;
import mx.marjan.ui.ThemeManager;
import mx.marjan.ui.Ui;

public class InvoicesView extends BaseView {

    private final InvoiceService service = new InvoiceService();
    private final ServiceRequestService requestService = new ServiceRequestService();
    private final ClientService clientService = new ClientService();

    private final RecordTable<Invoice> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Factura", Invoice::invoiceNumber),
            RecordTable.Column.of("Cliente", Invoice::clientName),
            RecordTable.Column.of("Solicitud", Invoice::requestFolio),
            RecordTable.Column.money("Importe", Invoice::amount),
            RecordTable.Column.money("Pagado", Invoice::paid),
            RecordTable.Column.money("Saldo", Invoice::balance),
            RecordTable.Column.of("Vence", invoice -> Dates.format(invoice.dueDate())),
            RecordTable.Column.badge("Estado", invoice -> invoice.status().label(),
                    invoice -> StatusTones.invoice(invoice.status()))));

    private final RecordTable<ServiceRequest> pendingTable = new RecordTable<>(List.of(
            RecordTable.Column.of("Folio", ServiceRequest::folio),
            RecordTable.Column.of("Cliente", ServiceRequest::clientName),
            RecordTable.Column.text("Ruta", ServiceRequest::routeLabel, 40),
            RecordTable.Column.badge("Estado", request -> request.status().label(),
                    request -> StatusTones.request(request.status())),
            RecordTable.Column.money("Tarifa", ServiceRequest::agreedRate),
            RecordTable.Column.of("Recoleccion", request -> Dates.format(request.pickupScheduled()))));

    private final ComboBox<Object> statusFilter = new ComboBox<>();
    private final ComboBox<Object> clientFilter = new ComboBox<>();

    public InvoicesView() {
        statusFilter.getItems().add("(todos)");
        for (InvoiceStatus status : InvoiceStatus.values()) {
            statusFilter.getItems().add(status);
        }
        statusFilter.setValue("(todos)");
        clientFilter.getItems().add("(todos)");
        clientFilter.setValue("(todos)");
        Ui.onDoubleClick(table, invoice -> openPayments());
        Ui.onDoubleClick(pendingTable, request -> billSelectedRequest());

        var filters = Ui.filters(new Label("Estado:"), statusFilter,
                new Label("Cliente:"), clientFilter,
                Ui.button("Buscar", this::reload));
        var actions = Ui.toolbar(
                Ui.primary("Facturar", this::openInvoiceForm),
                Ui.button("Registrar pago", this::openPaymentForm),
                Ui.button("Pagos", this::openPayments),
                Ui.button("Cancelar", this::cancelInvoice),
                Ui.button("Actualizar estatus", this::refreshStatuses),
                Ui.button("Eliminar", this::deleteInvoice),
                Ui.button("Recargar", this::reload));

        RecordTablePanel<ServiceRequest> pendingPanel = new RecordTablePanel<>(pendingTable);
        pendingPanel.setPadding(new Insets(12, 0, 0, 0));
        pendingPanel.withActions(
                Ui.button("Facturar seleccionada", this::billSelectedRequest),
                Ui.button("Recargar", this::reloadPending));

        Label pendingTitle = new Label("Por facturar (tarifa autorizada sin factura)");
        pendingTitle.getStyleClass().add("section-title");
        VBox pendingBox = new VBox(6, pendingTitle, pendingPanel);
        VBox.setVgrow(pendingPanel, javafx.scene.layout.Priority.ALWAYS);

        SplitPane split = new SplitPane(table, pendingBox);
        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.setDividerPositions(0.58);

        setTop(new VBox(4, filters, actions));
        setCenter(split);
        reloadClients();
        reload();
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

    @Override
    public void reload() {
        Object status = statusFilter.getValue();
        Object client = clientFilter.getValue();
        loadRows(() -> service.search(status instanceof InvoiceStatus s ? s : null,
                        client instanceof Client c ? c.id() : null),
                table::setRows);
        reloadPending();
    }

    private void reloadPending() {
        loadRows(requestService::pendingBilling, pendingTable::setRows);
    }

    private void billSelectedRequest() {
        ServiceRequest request = pendingTable.selected();
        if (request == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una solicitud por facturar");
            return;
        }
        Async.run(() -> service.createFromRequest(request, Dates.today(), request.agreedRate()), result -> {
            if (result.isErr()) {
                Ui.error(Ui.windowOf(this), "No se puede facturar", result.problems());
            } else {
                Ui.success(Ui.windowOf(this), "Factura creada: " + result.value().invoiceNumber());
                reload();
            }
        }, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private Invoice requireSelected() {
        Invoice invoice = table.selected();
        if (invoice == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una factura");
        }
        return invoice;
    }

    private void openInvoiceForm() {
        Async.run(() -> {
            List<ServiceRequest> billable = new ArrayList<>();
            billable.addAll(requestService.listByStatus(RequestStatus.DELIVERED));
            billable.addAll(requestService.listByStatus(RequestStatus.CLOSED));
            return billable;
        }, requests -> {
            if (requests.isEmpty()) {
                Ui.info(Ui.windowOf(this), "No hay solicitudes entregadas o cerradas por facturar");
                return;
            }
            FormPanel form = new FormPanel()
                    .addCombo("request", "Solicitud", requests.toArray(), requests.get(0))
                    .addText("issueDate", "Fecha de emision", Dates.format(Dates.today()),
                            "Formato: AAAA-MM-DD")
                    .addText("amount", "Importe", "", "Deje vacio para usar la tarifa autorizada");
            form.validate("issueDate", Validators.date());
            form.validate("amount", Validators.money());
            ModalForm.show(Ui.windowOf(this), "Nueva factura", form, () -> {
                ServiceRequest request = (ServiceRequest) form.selected("request");
                LocalDate issue = Dates.parseDate(form.text("issueDate")).orElse(null);
                if (issue == null) {
                    return Result.err("La fecha de emision es obligatoria (AAAA-MM-DD)");
                }
                BigDecimal amount;
                if (form.text("amount").isBlank()) {
                    amount = request.agreedRate();
                } else {
                    Result<BigDecimal> parsed = Money.require(form.text("amount"), "importe");
                    if (parsed.isErr()) {
                        return parsed;
                    }
                    amount = parsed.value();
                }
                return service.createFromRequest(request, issue, amount);
            }, this::reload);
        }, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void openPaymentForm() {
        Invoice invoice = requireSelected();
        if (invoice == null) {
            return;
        }
        FormPanel form = new FormPanel()
                .addText("amount", "Monto del pago", Money.zeroIfNull(invoice.balance()).toPlainString(),
                        "No puede exceder el saldo pendiente")
                .addText("date", "Fecha", Dates.format(Dates.today()), "Formato: AAAA-MM-DD")
                .addCombo("method", "Forma de pago", PaymentMethod.values(), PaymentMethod.CASH);
        form.validate("amount", Validators.money());
        form.validate("date", Validators.date());
        ModalForm.show(Ui.windowOf(this), "Pago de " + invoice.invoiceNumber(), form, () -> {
            Result<BigDecimal> amountResult = Money.require(form.text("amount"), "monto del pago");
            if (amountResult.isErr()) {
                return amountResult;
            }
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            return service.registerPayment(invoice.id(), amountResult.value(), date,
                    (PaymentMethod) form.selected("method"));
        }, this::reload);
    }

    private void openPayments() {
        Invoice invoice = requireSelected();
        if (invoice == null) {
            return;
        }
        RecordTable<Payment> payments = new RecordTable<>(List.of(
                RecordTable.Column.of("Fecha", payment -> Dates.format(payment.paymentDate())),
                RecordTable.Column.money("Monto", Payment::amount),
                RecordTable.Column.of("Forma", payment -> payment.method().label())));
        RecordTablePanel<Payment> panel = new RecordTablePanel<>(payments);

        Stage stage = new Stage();
        stage.initOwner(Ui.windowOf(this));
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Pagos de " + invoice.invoiceNumber());
        Runnable reload = () -> Async.run(() -> service.paymentsFor(invoice.id()),
                panel::setRows, failure -> Ui.failure(stage, failure));
        panel.withActions(
                Ui.button("Eliminar pago", () -> {
                    if (panel.selected() == null) {
                        Ui.info(stage, "Seleccione un pago");
                        return;
                    }
                    Ui.delete(stage, "el pago seleccionado",
                            () -> service.deletePayment(panel.selected().id()), reload);
                }),
                Ui.button("Recargar", reload),
                Ui.button("Cerrar", stage::close));
        BorderPane root = new BorderPane(panel);
        root.setPadding(new Insets(16));
        Scene scene = new Scene(root, 560, 340);
        ThemeManager.apply(scene);
        stage.setScene(scene);
        reload.run();
        stage.show();
    }

    private void cancelInvoice() {
        Invoice invoice = requireSelected();
        if (invoice == null) {
            return;
        }
        if (!Ui.confirm(Ui.windowOf(this), "\u00bfCancelar la factura " + invoice.invoiceNumber() + "?")) {
            return;
        }
        Async.run(() -> service.cancel(invoice.id()), result -> {
            if (result.isErr()) {
                Ui.error(Ui.windowOf(this), "No se puede cancelar", result.problems());
            } else {
                reload();
            }
        }, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void deleteInvoice() {
        Invoice invoice = requireSelected();
        if (invoice == null) {
            return;
        }
        Ui.delete(Ui.windowOf(this), "la factura " + invoice.invoiceNumber() + " y sus pagos",
                () -> service.delete(invoice.id()), this::reload);
    }

    private void refreshStatuses() {
        if (!Ui.confirm(Ui.windowOf(this), "\u00bfRecalcular el estatus de todas las facturas abiertas?")) {
            return;
        }
        Async.run(() -> service.refreshStatuses(Dates.today()), changed -> {
            Ui.success(Ui.windowOf(this), "Estatus actualizados: " + changed);
            reload();
        }, failure -> Ui.failure(Ui.windowOf(this), failure));
    }
}
