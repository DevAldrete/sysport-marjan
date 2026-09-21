package mx.marjan.reports;

import java.awt.BorderLayout;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Ui;

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

    private final ReportRepository repository = new ReportRepository();
    private final JComboBox<Kind> kind = new JComboBox<>(Kind.values());
    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final DefaultTableModel tableModel = new DefaultTableModel();
    private final JTable table = new JTable(tableModel);

    private Report current;

    public ReportsView() {
        LocalDate today = Dates.today();
        fromField.setText(Dates.format(today.withDayOfYear(1)));
        toField.setText(Dates.format(today));
        add(Ui.row(new JLabel("Reporte:"), kind,
                new JLabel("Desde:"), fromField,
                new JLabel("Hasta:"), toField,
                Ui.button("Generar", this::generate),
                Ui.button("Exportar CSV", this::exportCsv)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        generate();
    }

    private void generate() {
        Kind selected = (Kind) kind.getSelectedItem();
        LocalDate from = Dates.parseDate(fromField.getText()).orElse(Dates.today().withDayOfYear(1));
        LocalDate to = Dates.parseDate(toField.getText()).orElse(Dates.today());
        Async.run(() -> switch (selected) {
            case REVENUE -> repository.revenueByClient(from, to);
            case ROUTES -> repository.routeUsage(from, to);
            case VEHICLES -> repository.vehicleUsage(from, to);
            case FUEL -> repository.fuelEfficiency(from, to);
            case PROFITABILITY -> repository.profitability(from, to);
            case RECEIVABLES -> repository.receivables();
            case LICENSES -> repository.expiringLicenses(Dates.today());
            case MAINTENANCE -> repository.maintenanceDue(Dates.today());
        }, this::show, failure -> Ui.failure(this, failure));
    }

    private void show(Report report) {
        current = report;
        Object[] headers = report.headers().toArray();
        List<List<Object>> rows = report.rows();
        Object[][] data = new Object[rows.size()][];
        for (int i = 0; i < rows.size(); i++) {
            data[i] = rows.get(i).toArray();
        }
        tableModel.setDataVector(data, headers);
    }

    private void exportCsv() {
        if (current == null) {
            Ui.info(this, "Genere un reporte primero");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(current.title().replace(' ', '_') + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            CsvExporter.write(current, chooser.getSelectedFile());
            Ui.info(this, "Reporte exportado a " + chooser.getSelectedFile());
        } catch (IOException failure) {
            Ui.failure(this, failure);
        }
    }
}
