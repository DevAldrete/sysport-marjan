package mx.marjan.routes;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

public class RoutesView extends BaseView {

    private final RouteService service = new RouteService();
    private final RecordTableModel<Route> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Origen", Route::origin),
            RecordTableModel.Column.of("Destino", Route::destination),
            RecordTableModel.Column.of("Km estimados", Route::estimatedKm),
            RecordTableModel.Column.of("Descripcion", Route::description)));
    private final JTable table = Ui.table(model);
    private final JTextField searchField = new JTextField(18);

    public RoutesView() {
        add(Ui.row(new JLabel("Buscar:"), searchField,
                Ui.button("Buscar", this::reload),
                Ui.button("Nuevo", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    @Override
    public void reload() {
        String term = searchField.getText();
        load(() -> service.search(term), model::setRows);
    }

    private Route selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void openNew() {
        openForm(Route.empty());
    }

    private void openEdit() {
        Route route = selected();
        if (route == null) {
            Ui.info(this, "Seleccione una ruta");
            return;
        }
        openForm(route);
    }

    private void openForm(Route route) {
        boolean isNew = route.id() == 0;
        FormPanel form = new FormPanel()
                .addText("origin", "Origen", route.origin())
                .addText("destination", "Destino", route.destination())
                .addText("km", "Km estimados", route.estimatedKm() == null ? "0" : route.estimatedKm().toPlainString())
                .addArea("description", "Descripcion", route.description());
        ModalForm.show(this, isNew ? "Nueva ruta" : "Editar ruta", form, () -> {
            BigDecimal km = form.text("km").isBlank() ? BigDecimal.ZERO
                    : parse(form.text("km"));
            if (km == null) {
                return Result.err("Los km estimados deben ser un numero");
            }
            Route built = new Route(route.id(), form.text("origin"), form.text("destination"),
                    km, form.text("description"));
            return service.save(built);
        }, this::reload);
    }

    private BigDecimal parse(String text) {
        try {
            return new BigDecimal(text.replace(",", ""));
        } catch (NumberFormatException failure) {
            return null;
        }
    }
}
