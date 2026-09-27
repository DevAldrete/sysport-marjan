package mx.marjan.routes;

import java.math.BigDecimal;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Result;
import mx.marjan.shared.Validators;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.Icons;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class RoutesView extends BaseView {

    private final RouteService service = new RouteService();
    private final RecordTable<Route> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Origen", Route::origin),
            RecordTable.Column.of("Destino", Route::destination),
            RecordTable.Column.number("Km estimados", Route::estimatedKm),
            RecordTable.Column.text("Descripcion", Route::description, 60)));
    private final TextField search = new TextField();

    public RoutesView() {
        search.setPromptText("Origen o destino");
        search.setOnAction(event -> reload());
        Ui.onDoubleClick(table, this::openForm);

        var nuevo = Ui.primary("Nuevo", this::openNew);
        nuevo.setGraphic(Icons.action(Feather.PLUS));
        var filters = Ui.filters(new Label("Buscar:"), search, Ui.button("Buscar", this::reload));
        var actions = Ui.toolbar(nuevo,
                Ui.button("Editar", this::openEdit),
                Ui.button("Eliminar", this::deleteRoute),
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
        openForm(Route.empty());
    }

    private void openEdit() {
        Route route = table.selected();
        if (route == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una ruta");
            return;
        }
        openForm(route);
    }

    private void openForm(Route route) {
        boolean isNew = route.id() == 0;
        FormPanel form = new FormPanel()
                .addText("origin", "Origen", route.origin(), "Ciudad o punto de salida")
                .addText("destination", "Destino", route.destination(), "Ciudad o punto de entrega")
                .addText("km", "Km estimados",
                        route.estimatedKm() == null ? "0" : route.estimatedKm().toPlainString(),
                        "Distancia aproximada en kilometros")
                .addArea("description", "Descripcion", route.description(), "Notas de la ruta (opcional)");
        form.validate("km", Validators.number());
        ModalForm.show(Ui.windowOf(this), isNew ? "Nueva ruta" : "Editar ruta", form, () -> {
            BigDecimal km = Numbers.parseOrZero(form.text("km"));
            if (km == null) {
                return Result.err("Los km estimados deben ser un numero");
            }
            Route built = new Route(route.id(), form.text("origin"), form.text("destination"),
                    km, form.text("description"));
            return service.save(built);
        }, this::reload);
    }

    private void deleteRoute() {
        Route route = table.selected();
        if (route == null) {
            Ui.info(Ui.windowOf(this), "Seleccione una ruta");
            return;
        }
        Ui.delete(Ui.windowOf(this), "la ruta \"" + route.label() + "\"",
                () -> service.delete(route.id()), this::reload);
    }
}
