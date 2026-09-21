package mx.marjan.finance;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTable;
import mx.marjan.clients.Client;
import mx.marjan.clients.ClientService;
import mx.marjan.requests.RequestStatus;
import mx.marjan.requests.ServiceRequest;
import mx.marjan.requests.ServiceRequestService;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

public class InvoicesView extends BaseView {

    private final InvoiceService service = new InvoiceService();
    private final ServiceRequestService requestService = new ServiceRequestService();
    private final ClientService clientService = new ClientService();
    private final RecordTableModel<Invoice> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Factura", Invoice::invoiceNumber),
            RecordTableModel.Column.of("Cliente", Invoice::clientName),
            RecordTableModel.Column.of("Solicitud", Invoice::requestFolio),
            RecordTableModel.Column.of("Importe", invoice -> Money.format(invoice.amount())),
            RecordTableModel.Column.of("Pagado", invoice -> Money.format(invoice.paid())),
            RecordTableModel.Column.of("Saldo", invoice -> Money.format(invoice.balance())),
            RecordTableModel.Column.of("Vence", invoice -> Dates.format(invoice.dueDate())),
            RecordTableModel.Column.of("Estado", invoice -> invoice.status().label())));
    private final JTable table = Ui.table(model);
    private final JComboBox<Object> statusFilter = new JComboBox<>();
    private final JComboBox<Object> clientFilter = new JComboBox<>();

    public InvoicesView() {
        statusFilter.addItem("(todos)");
        for (InvoiceStatus status : InvoiceStatus.values()) {
            statusFilter.addItem(status);
        }
        add(Ui.row(new JLabel("Estado:"), statusFilter, new JLabel("Cliente:"), clientFilter,
                Ui.button("Buscar", this::reload),
                Ui.button("Facturar", this::openInvoiceForm),
                Ui.button("Registrar pago", this::openPaymentForm),
                Ui.button("Actualizar estatus", this::refreshStatuses),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reloadClients();
        reload();
    }

    private void reloadClients() {
        load(clientService::listActive, clients -> {
            clientFilter.removeAllItems();
            clientFilter.addItem("(todos)");
            for (Client client : clients) {
                clientFilter.addItem(client);
            }
        });
    }

    @Override
    public void reload() {
        Object status = statusFilter.getSelectedItem();
        Object client = clientFilter.getSelectedItem();
        load(() -> service.search(status instanceof InvoiceStatus s ? s : null,
                        client instanceof Client c ? c.id() : null),
                model::setRows);
    }

    private Invoice selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void openInvoiceForm() {
        Async.run(() -> {
            List<ServiceRequest> billable = new ArrayList<>();
            billable.addAll(requestService.listByStatus(RequestStatus.DELIVERED));
            billable.addAll(requestService.listByStatus(RequestStatus.CLOSED));
            return billable;
        }, requests -> {
            if (requests.isEmpty()) {
                Ui.info(this, "No hay solicitudes entregadas o cerradas por facturar");
                return;
            }
            FormPanel form = new FormPanel()
                    .addCombo("request", "Solicitud", requests.toArray(), requests.get(0))
                    .addText("issueDate", "Fecha de emision (yyyy-MM-dd)", Dates.format(Dates.today()))
                    .addText("amount", "Importe", "");
            ModalForm.show(this, "Nueva factura", form, () -> {
                ServiceRequest request = (ServiceRequest) form.selected("request");
                LocalDate issue = Dates.parseDate(form.text("issueDate")).orElse(null);
                if (issue == null) {
                    return Result.err("La fecha de emision es obligatoria (yyyy-MM-dd)");
                }
                BigDecimal amount = form.text("amount").isBlank() ? request.agreedRate()
                        : Money.parse(form.text("amount")).orElse(null);
                return service.createFromRequest(request, issue, amount);
            }, this::reload);
        }, failure -> Ui.failure(this, failure));
    }

    private void openPaymentForm() {
        Invoice invoice = selected();
        if (invoice == null) {
            Ui.info(this, "Seleccione una factura");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("amount", "Monto del pago", Money.zeroIfNull(invoice.balance()).toPlainString())
                .addText("date", "Fecha (yyyy-MM-dd)", Dates.format(Dates.today()))
                .addCombo("method", "Forma de pago", PaymentMethod.values(), PaymentMethod.CASH);
        ModalForm.show(this, "Pago de " + invoice.invoiceNumber(), form, () -> {
            BigDecimal amount = Money.parse(form.text("amount")).orElse(BigDecimal.ZERO);
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            return service.registerPayment(invoice.id(), amount, date,
                    (PaymentMethod) form.selected("method"));
        }, this::reload);
    }

    private void refreshStatuses() {
        Async.run(() -> service.refreshStatuses(Dates.today()),
                changed -> {
                    Ui.info(this, "Estatus actualizados: " + changed);
                    reload();
                },
                failure -> Ui.failure(this, failure));
    }
}
