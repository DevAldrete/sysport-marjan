package mx.marjan.clients;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import mx.marjan.routes.Route;
import mx.marjan.routes.RouteService;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

public class ClientsView extends BaseView {

    private final ClientService service = new ClientService();
    private final RouteService routeService = new RouteService();
    private final RecordTableModel<Client> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Nombre", Client::name),
            RecordTableModel.Column.of("RFC", Client::rfc),
            RecordTableModel.Column.of("Contacto", Client::contactName),
            RecordTableModel.Column.of("Tipo", client -> client.clientType().label()),
            RecordTableModel.Column.of("Pago", client -> client.paymentTerms().label()),
            RecordTableModel.Column.of("Credito", client -> Money.format(client.creditLimit())),
            RecordTableModel.Column.of("Estado", client -> client.status().label())));
    private final JTable table = Ui.table(model);
    private final JTextField searchField = new JTextField(18);

    public ClientsView() {
        add(Ui.row(new JLabel("Buscar:"), searchField,
                Ui.button("Buscar", this::reload),
                Ui.button("Nuevo", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Desactivar", () -> changeStatus(ClientStatus.INACTIVE)),
                Ui.button("Activar", () -> changeStatus(ClientStatus.ACTIVE)),
                Ui.button("Tarifas", this::openRates),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    @Override
    public void reload() {
        String term = searchField.getText();
        load(() -> service.search(term), model::setRows);
    }

    private Client selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void openNew() {
        openForm(Client.empty());
    }

    private void openEdit() {
        Client client = selected();
        if (client == null) {
            Ui.info(this, "Seleccione un cliente");
            return;
        }
        openForm(client);
    }

    private void openForm(Client client) {
        boolean isNew = client.id() == 0;
        FormPanel form = new FormPanel()
                .addText("name", "Nombre / Razon social", client.name())
                .addText("rfc", "RFC", client.rfc())
                .addText("address", "Domicilio", client.address())
                .addText("phone", "Telefono", client.phone())
                .addText("email", "Correo", client.email())
                .addText("contact", "Contacto", client.contactName())
                .addCombo("type", "Tipo", ClientType.values(), client.clientType())
                .addCombo("terms", "Condiciones", PaymentTerms.values(), client.paymentTerms())
                .addText("creditLimit", "Limite de credito", plain(client.creditLimit()))
                .addText("creditDays", "Dias de credito", String.valueOf(client.creditDays()))
                .addCombo("status", "Estado", ClientStatus.values(), client.status());
        ModalForm.show(this, isNew ? "Nuevo cliente" : "Editar cliente", form, () -> {
            BigDecimal limit = Money.parse(form.text("creditLimit")).orElse(BigDecimal.ZERO);
            int days;
            try {
                days = Integer.parseInt(form.text("creditDays").isBlank() ? "0" : form.text("creditDays"));
            } catch (NumberFormatException failure) {
                return Result.err("Los dias de credito deben ser un numero");
            }
            Client built = new Client(client.id(), form.text("name"), form.text("rfc"),
                    form.text("address"), form.text("phone"), form.text("email"), form.text("contact"),
                    (ClientType) form.selected("type"), (PaymentTerms) form.selected("terms"),
                    limit, days, (ClientStatus) form.selected("status"));
            return service.save(built);
        }, this::reload);
    }

    private void changeStatus(ClientStatus status) {
        Client client = selected();
        if (client == null) {
            Ui.info(this, "Seleccione un cliente");
            return;
        }
        Async.run(() -> status == ClientStatus.INACTIVE ? service.deactivate(client.id())
                : service.activate(client.id()),
                result -> {
                    if (result.isErr()) {
                        Ui.error(this, "Error", result.problems());
                    } else {
                        reload();
                    }
                },
                failure -> Ui.failure(this, failure));
    }

    private void openRates() {
        Client client = selected();
        if (client == null) {
            Ui.info(this, "Seleccione un cliente");
            return;
        }
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Tarifas de " + client.name(), Dialog.ModalityType.APPLICATION_MODAL);
        RecordTableModel<ClientRate> rateModel = new RecordTableModel<>(List.of(
                RecordTableModel.Column.of("Ruta", ClientRate::routeLabel),
                RecordTableModel.Column.of("Tarifa", rate -> Money.format(rate.rate())),
                RecordTableModel.Column.of("Desde", rate -> Dates.format(rate.validFrom())),
                RecordTableModel.Column.of("Hasta", rate -> Dates.format(rate.validTo()))));
        JTable rateTable = Ui.table(rateModel);
        Runnable reloadRates = () -> Async.run(() -> service.ratesFor(client.id()),
                rateModel::setRows, failure -> Ui.failure(dialog, failure));
        ClientRate[] cache = new ClientRate[1];
        rateTable.getSelectionModel().addListSelectionListener(event -> {
            int row = rateTable.getSelectedRow();
            cache[0] = row < 0 ? null : rateModel.rowAt(rateTable.convertRowIndexToModel(row));
        });
        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(Ui.scroll(rateTable), BorderLayout.CENTER);
        dialog.add(Ui.row(
                Ui.button("Nueva tarifa", () -> openRateForm(client, null, reloadRates)),
                Ui.button("Editar", () -> openRateForm(client, cache[0], reloadRates)),
                Ui.button("Cerrar", dialog::dispose)), BorderLayout.SOUTH);
        reloadRates.run();
        dialog.setSize(600, 360);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openRateForm(Client client, ClientRate rate, Runnable onSaved) {
        Async.run(routeService::listAll, routes -> showRateForm(client, rate, routes, onSaved),
                failure -> Ui.failure(this, failure));
    }

    private void showRateForm(Client client, ClientRate rate, List<Route> routes, Runnable onSaved) {
        ClientRate editing = rate != null ? rate : ClientRate.empty();
        FormPanel form = new FormPanel()
                .addCombo("route", "Ruta", routes.toArray(), routeById(routes, editing.routeId()))
                .addText("rate", "Tarifa", plain(editing.rate()))
                .addText("from", "Vigente desde (yyyy-MM-dd)", Dates.format(editing.validFrom()))
                .addText("to", "Vigente hasta (opcional)", Dates.format(editing.validTo()));
        ModalForm.show(this, rate == null ? "Nueva tarifa" : "Editar tarifa", form, () -> {
            Object routeValue = form.selected("route");
            if (!(routeValue instanceof Route route)) {
                return Result.err("Debe seleccionar una ruta");
            }
            BigDecimal amount = Money.parse(form.text("rate")).orElse(BigDecimal.ZERO);
            java.time.LocalDate from = Dates.parseDate(form.text("from")).orElse(null);
            if (from == null) {
                return Result.err("La fecha de vigencia inicial es obligatoria (yyyy-MM-dd)");
            }
            java.time.LocalDate to = form.text("to").isBlank() ? null
                    : Dates.parseDate(form.text("to")).orElse(null);
            ClientRate built = new ClientRate(editing.id(), client.id(), route.id(), route.label(),
                    amount, from, to);
            return service.saveRate(built);
        }, onSaved);
    }

    private Route routeById(List<Route> routes, long id) {
        return routes.stream().filter(route -> route.id() == id).findFirst().orElse(null);
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
