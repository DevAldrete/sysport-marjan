package mx.marjan.clients;

import java.math.BigDecimal;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import mx.marjan.routes.Route;
import mx.marjan.routes.RouteService;
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Money;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.Icons;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.StatusBadge;
import mx.marjan.ui.ThemeManager;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class ClientsView extends BaseView {

    private final ClientService service = new ClientService();
    private final RouteService routeService = new RouteService();
    private final RecordTable<Client> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Nombre", Client::name),
            RecordTable.Column.of("RFC", Client::rfc),
            RecordTable.Column.of("Contacto", Client::contactName),
            RecordTable.Column.of("Tipo", client -> client.clientType().label()),
            RecordTable.Column.of("Pago", client -> client.paymentTerms().label()),
            RecordTable.Column.money("Credito", Client::creditLimit),
            RecordTable.Column.badge("Estado", client -> client.status().label(),
                    client -> client.status() == ClientStatus.ACTIVE
                            ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.NEUTRAL)));
    private final TextField search = new TextField();

    public ClientsView() {
        search.setPromptText("Nombre o RFC");
        search.setOnAction(event -> reload());
        Ui.onDoubleClick(table, client -> openForm(client));

        var nuevo = Ui.primary("Nuevo", this::openNew);
        nuevo.setGraphic(Icons.action(Feather.PLUS));
        var filters = Ui.filters(new Label("Buscar:"), search, Ui.button("Buscar", this::reload));
        var actions = Ui.toolbar(nuevo,
                Ui.button("Editar", this::openEdit),
                Ui.button("Tarifas", this::openRates),
                Ui.button("Activar", () -> changeStatus(ClientStatus.ACTIVE)),
                Ui.button("Desactivar", () -> changeStatus(ClientStatus.INACTIVE)),
                Ui.button("Eliminar", this::deleteClient),
                Ui.button("Recargar", this::reload));
        setTop(new VBox(4, filters, actions));
        setCenter(table);
        reload();
    }

    @Override
    public void reload() {
        loadRows(() -> service.search(search.getText()), table::setRows);
    }

    private void openNew() {
        openForm(Client.empty());
    }

    private void openEdit() {
        Client client = table.selected();
        if (client == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un cliente");
            return;
        }
        openForm(client);
    }

    private void openForm(Client client) {
        boolean isNew = client.id() == 0;
        FormPanel form = new FormPanel()
                .addText("name", "Nombre / Razon social", client.name(), "Como aparece en la factura")
                .addText("rfc", "RFC", client.rfc(), "13 caracteres persona moral, 12 persona fisica")
                .addText("address", "Domicilio", client.address())
                .addText("phone", "Telefono", client.phone())
                .addText("email", "Correo", client.email())
                .addText("contact", "Contacto", client.contactName(), "Persona de contacto")
                .addCombo("type", "Tipo", ClientType.values(), client.clientType())
                .addCombo("terms", "Condiciones", PaymentTerms.values(), client.paymentTerms())
                .addText("creditLimit", "Limite de credito", Numbers.plain(client.creditLimit()),
                        "Monto maximo a credito")
                .addText("creditDays", "Dias de credito", String.valueOf(client.creditDays()),
                        "Dias para pagar, ej. 30")
                .addCombo("status", "Estado", ClientStatus.values(), client.status());
        form.validate("creditLimit", Validators.money());
        form.validate("creditDays", Validators.number());
        ModalForm.show(Ui.windowOf(this), isNew ? "Nuevo cliente" : "Editar cliente", form, () -> {
            Result<BigDecimal> limitResult = Money.require(form.text("creditLimit"), "limite de credito");
            if (limitResult.isErr()) {
                return limitResult;
            }
            int days;
            try {
                days = Integer.parseInt(form.text("creditDays").isBlank() ? "0" : form.text("creditDays"));
            } catch (NumberFormatException failure) {
                return Result.err("Los dias de credito deben ser un numero");
            }
            Client built = new Client(client.id(), form.text("name"), form.text("rfc"),
                    form.text("address"), form.text("phone"), form.text("email"), form.text("contact"),
                    (ClientType) form.selected("type"), (PaymentTerms) form.selected("terms"),
                    limitResult.value(), days, (ClientStatus) form.selected("status"));
            return service.save(built);
        }, this::reload);
    }

    private void changeStatus(ClientStatus status) {
        Client client = table.selected();
        if (client == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un cliente");
            return;
        }
        String action = status == ClientStatus.INACTIVE ? "Desactivar" : "Activar";
        if (!Ui.confirm(Ui.windowOf(this), action + " el cliente \"" + client.name() + "\"?")) {
            return;
        }
        Async.run(() -> status == ClientStatus.INACTIVE ? service.deactivate(client.id())
                : service.activate(client.id()),
                result -> {
                    if (result.isErr()) {
                        Ui.error(Ui.windowOf(this), "No se pudo cambiar el estado", result.problems());
                    } else {
                        reload();
                    }
                },
                failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void deleteClient() {
        Client client = table.selected();
        if (client == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un cliente");
            return;
        }
        Ui.delete(Ui.windowOf(this), "el cliente \"" + client.name() + "\"",
                () -> service.delete(client.id()), this::reload);
    }

    private void openRates() {
        Client client = table.selected();
        if (client == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un cliente");
            return;
        }
        RecordTable<ClientRate> rates = new RecordTable<>(List.of(
                RecordTable.Column.of("Ruta", ClientRate::routeLabel),
                RecordTable.Column.money("Tarifa", ClientRate::rate),
                RecordTable.Column.of("Desde", rate -> Dates.format(rate.validFrom())),
                RecordTable.Column.of("Hasta", rate -> Dates.format(rate.validTo()))));

        Runnable reload = () -> Async.run(() -> service.ratesFor(client.id()),
                rates::setRows, failure -> Ui.failure(Ui.windowOf(rates), failure));
        Ui.onDoubleClick(rates, rate -> openRateForm(client, rate, reload));

        Stage stage = new Stage();
        stage.initOwner(Ui.windowOf(this));
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle("Tarifas de " + client.name());
        var actions = Ui.toolbar(
                Ui.button("Nueva tarifa", () -> openRateForm(client, null, reload)),
                Ui.button("Editar", () -> {
                    if (rates.selected() == null) {
                        Ui.info(stage, "Seleccione una tarifa");
                        return;
                    }
                    openRateForm(client, rates.selected(), reload);
                }),
                Ui.button("Eliminar", () -> {
                    if (rates.selected() == null) {
                        Ui.info(stage, "Seleccione una tarifa");
                        return;
                    }
                    Ui.delete(stage, "la tarifa seleccionada",
                            () -> service.deleteRate(rates.selected().id()), reload);
                }),
                Ui.button("Cerrar", stage::close));
        BorderPane root = new BorderPane(rates);
        root.setPadding(new javafx.geometry.Insets(16));
        root.setBottom(actions);
        Scene scene = new Scene(root, 760, 480);
        ThemeManager.apply(scene);
        stage.setScene(scene);
        reload.run();
        stage.show();
    }

    private void openRateForm(Client client, ClientRate rate, Runnable onSaved) {
        Async.run(routeService::listAll, routes -> showRateForm(client, rate, routes, onSaved),
                failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private void showRateForm(Client client, ClientRate rate, List<Route> routes, Runnable onSaved) {
        ClientRate editing = rate != null ? rate : ClientRate.empty();
        FormPanel form = new FormPanel()
                .addCombo("route", "Ruta", routes.toArray(), routeById(routes, editing.routeId()))
                .addText("rate", "Tarifa", Numbers.plain(editing.rate()), "Importe por viaje")
                .addText("from", "Vigente desde", Dates.format(editing.validFrom()), "Formato: AAAA-MM-DD")
                .addText("to", "Vigente hasta (opcional)", Dates.format(editing.validTo()), "Formato: AAAA-MM-DD");
        form.validate("rate", Validators.money());
        form.validate("from", Validators.date());
        form.validate("to", Validators.date());
        ModalForm.show(Ui.windowOf(this), rate == null ? "Nueva tarifa" : "Editar tarifa", form, () -> {
            Object routeValue = form.selected("route");
            if (!(routeValue instanceof Route route)) {
                return Result.err("Debe seleccionar una ruta");
            }
            Result<BigDecimal> rateResult = Money.require(form.text("rate"), "tarifa");
            if (rateResult.isErr()) {
                return rateResult;
            }
            java.time.LocalDate from = Dates.parseDate(form.text("from")).orElse(null);
            if (from == null) {
                return Result.err("La fecha de vigencia inicial es obligatoria (yyyy-MM-dd)");
            }
            java.time.LocalDate to = form.text("to").isBlank() ? null
                    : Dates.parseDate(form.text("to")).orElse(null);
            ClientRate built = new ClientRate(editing.id(), client.id(), route.id(), route.label(),
                    rateResult.value(), from, to);
            return service.saveRate(built);
        }, onSaved);
    }

    private Route routeById(List<Route> routes, long id) {
        return routes.stream().filter(route -> route.id() == id).findFirst().orElse(null);
    }
}
