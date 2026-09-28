package mx.marjan.routes;

import mx.marjan.shared.Numbers;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;
import mx.marjan.shared.Validators;

public class RoutesView extends BaseView {

    private final RouteService service = new RouteService();
    private final RecordTableModel<Route> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.text("Ruta", Route::label, 80),
            RecordTableModel.Column.of("Km estimados", Route::estimatedKm),
            RecordTableModel.Column.text("Descripcion", Route::description, 60)));
    private final JTable table = Ui.table(model);
    private final JTextField searchField = new JTextField(18);

    public RoutesView() {
        Ui.onEnter(searchField, this::reload);
        Ui.onDoubleClick(table, this::openEdit);
        add(Ui.row(new JLabel("Buscar:"), searchField,
                Ui.button("Buscar", this::reload),
                Ui.button("Nuevo", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Eliminar", this::deleteRoute),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    private void deleteRoute() {
        Route route = selected();
        if (route == null) {
            Ui.info(this, "Seleccione una ruta");
            return;
        }
        Ui.delete(this, "la ruta \"" + route.label() + "\"",
                () -> service.delete(route.id()), this::reload);
    }

    @Override
    public void reload() {
        String term = searchField.getText();
        loadRows(() -> service.search(term), model::setRows);
    }

    private Route selected() {
        return selectedRow(table, model);
    }

    private void openNew() {
        openForm(Route.empty(), List.of());
    }

    private void openEdit() {
        Route route = selected();
        if (route == null) {
            Ui.info(this, "Seleccione una ruta");
            return;
        }
        Async.run(() -> service.stops(route.id()),
                stops -> openForm(route, stops),
                failure -> Ui.failure(this, failure));
    }

    private void openForm(Route route, List<RouteStop> stops) {
        boolean isNew = route.id() == 0;
        RouteStopEditorPanel routeStops = new RouteStopEditorPanel(route.id(), stops);
        FormPanel form = new FormPanel()
                .addText("km", "Km estimados", route.estimatedKm() == null ? "0" : route.estimatedKm().toPlainString(),
                        "Distancia aproximada en kilometros")
                .addArea("description", "Descripcion", route.description(), "Notas de la ruta (opcional)");
        form.validate("km", Validators.number());
        form.addSection(routeStops);
        ModalForm.show(this, isNew ? "Nueva ruta" : "Editar ruta", form, () -> {
            BigDecimal km = Numbers.parseOrZero(form.text("km"));
            if (km == null) {
                return Result.err("Los km estimados deben ser un numero");
            }
            Route built = new Route(route.id(), "", "", km, form.text("description"), "");
            return service.save(built, routeStops.stops());
        }, this::reload);
    }
}
