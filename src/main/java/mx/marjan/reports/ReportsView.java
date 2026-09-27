package mx.marjan.reports;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import mx.marjan.shared.Dates;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.Icons;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class ReportsView extends BaseView {

    private enum Kind {
        REVENUE("Ingresos por cliente"),
        ROUTES("Rutas mas utilizadas"),
        VEHICLES("Viajes por unidad"),
        FUEL("Rendimiento de combustible"),
        PROFITABILITY("Rentabilidad por viaje"),
        RECEIVABLES("Saldos por cobrar"),
        LICENSES("Licencias por vencer"),
        MAINTENANCE("Mantenimiento proximo");

        private final String label;

        Kind(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ReportService service = new ReportService();
    private final ComboBox<Kind> kind = new ComboBox<>();
    private final DatePicker from = new DatePicker();
    private final DatePicker to = new DatePicker();
    private final TableView<List<Object>> table = new TableView<>();

    private Report current;

    public ReportsView() {
        kind.getItems().setAll(Kind.values());
        kind.setValue(Kind.REVENUE);
        LocalDate today = Dates.today();
        from.setValue(today.withDayOfYear(1));
        to.setValue(today);

        table.getStyleClass().add("data-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Genere un reporte para ver resultados"));

        var generar = Ui.primary("Generar", this::generate);
        generar.setGraphic(Icons.action(Feather.REFRESH_CW));
        var actions = Ui.toolbar(new Label("Reporte:"), kind,
                new Label("Desde:"), from, new Label("Hasta:"), to,
                generar,
                Ui.button("Exportar CSV", this::exportCsv));
        setTop(new VBox(actions));
        setCenter(table);
    }

    @Override
    public void reload() {
        generate();
    }

    private void generate() {
        Kind selected = kind.getValue();
        LocalDate start = from.getValue() == null ? Dates.today().withDayOfYear(1) : from.getValue();
        LocalDate end = to.getValue() == null ? Dates.today() : to.getValue();
        load(() -> switch (selected) {
            case REVENUE -> service.revenueByClient(start, end);
            case ROUTES -> service.routeUsage(start, end);
            case VEHICLES -> service.vehicleUsage(start, end);
            case FUEL -> service.fuelEfficiency(start, end);
            case PROFITABILITY -> service.profitability(start, end);
            case RECEIVABLES -> service.receivables();
            case LICENSES -> service.expiringLicenses(Dates.today());
            case MAINTENANCE -> service.maintenanceDue(Dates.today());
        }, result -> {
            if (result.isErr()) {
                Ui.error(Ui.windowOf(this), "Reporte", result.problems());
            } else {
                show(result.value());
            }
        });
    }

    private void show(Report report) {
        current = report;
        table.getColumns().clear();
        List<String> headers = report.headers();
        for (int i = 0; i < headers.size(); i++) {
            final int index = i;
            TableColumn<List<Object>, Object> column = new TableColumn<>(headers.get(i));
            column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(
                    index < cell.getValue().size() ? cell.getValue().get(index) : null));
            column.setSortable(true);
            table.getColumns().add(column);
        }
        table.getItems().setAll(report.rows());
        setStatus(report.rows().isEmpty() ? "Sin resultados" : " ");
    }

    private void exportCsv() {
        if (current == null) {
            Ui.info(Ui.windowOf(this), "Genere un reporte primero");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exportar reporte");
        chooser.setInitialFileName(current.title().replace(' ', '_') + ".csv");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        File file = chooser.showSaveDialog(Ui.windowOf(this));
        if (file == null) {
            return;
        }
        try {
            CsvExporter.write(current, file);
            Ui.success(Ui.windowOf(this), "Reporte exportado a " + file);
        } catch (IOException failure) {
            Ui.failure(Ui.windowOf(this), failure);
        }
    }
}
