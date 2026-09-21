package mx.marjan.trips;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import mx.marjan.finance.Advance;
import mx.marjan.finance.AdvanceService;
import mx.marjan.finance.Expense;
import mx.marjan.finance.ExpenseService;
import mx.marjan.finance.ExpenseType;
import mx.marjan.fleet.FuelLoad;
import mx.marjan.fleet.FuelService;
import mx.marjan.shared.Async;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Money;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

/** Trip detail with tabs for costs, fuel, advances, incidents and delivery. */
public class TripDetailDialog extends JDialog {

    private final Trip trip;
    private final ExpenseService expenseService = new ExpenseService();
    private final FuelService fuelService = new FuelService();
    private final AdvanceService advanceService = new AdvanceService();
    private final IncidentService incidentService = new IncidentService();
    private final DeliveryService deliveryService = new DeliveryService();

    public static void show(Component parent, Trip trip) {
        new TripDetailDialog(parent, trip).setVisible(true);
    }

    private TripDetailDialog(Component parent, Trip trip) {
        super(SwingUtilities.getWindowAncestor(parent), "Viaje " + trip.folio(),
                Dialog.ModalityType.APPLICATION_MODAL);
        this.trip = trip;
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Resumen", summaryPanel());
        tabs.addTab("Gastos", expensesPanel());
        tabs.addTab("Combustible", fuelPanel());
        tabs.addTab("Anticipos", advancesPanel());
        tabs.addTab("Incidencias", incidentsPanel());
        tabs.addTab("Entrega", deliveryPanel());
        setLayout(new BorderLayout(8, 8));
        add(tabs, BorderLayout.CENTER);
        add(Ui.row(Ui.button("Cerrar", this::dispose)), BorderLayout.SOUTH);
        setSize(880, 560);
        setLocationRelativeTo(parent);
    }

    private JPanel summaryPanel() {
        JLabel tripInfo = new JLabel();
        JLabel costs = new JLabel();
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.titled("Viaje", tripInfo), BorderLayout.NORTH);
        panel.add(Ui.titled("Costos", costs), BorderLayout.CENTER);
        Async.run(() -> {
            BigDecimal expenses = expenseService.listByTrip(trip.id()).stream()
                    .map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal fuel = fuelService.listByTrip(trip.id()).stream()
                    .map(FuelLoad::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Object[] { expenses, fuel, advanceService.balanceForTrip(trip.id()) };
        }, values -> {
            BigDecimal expenses = (BigDecimal) values[0];
            BigDecimal fuel = (BigDecimal) values[1];
            tripInfo.setText("<html>Unidad: " + trip.vehicleLabel() + "<br>Operador: " + trip.employeeName()
                    + "<br>Ruta: " + trip.routeLabel() + "<br>Estado: " + trip.status().label()
                    + "<br>Salida: " + Dates.format(trip.departure())
                    + "<br>Llegada: " + Dates.format(trip.arrival()) + "</html>");
            costs.setText("<html>Gastos: " + Money.format(expenses)
                    + "<br>Combustible: " + Money.format(fuel)
                    + "<br>Total: " + Money.format(expenses.add(fuel))
                    + "<br>Anticipo: " + ((mx.marjan.finance.AdvanceBalance) values[2]).label()
                    + "</html>");
        }, failure -> Ui.failure(this, failure));
        return panel;
    }

    private JPanel expensesPanel() {
        RecordTableModel<Expense> model = new RecordTableModel<>(java.util.List.of(
                RecordTableModel.Column.of("Fecha", expense -> Dates.format(expense.expenseDate())),
                RecordTableModel.Column.of("Tipo", expense -> expense.type().label()),
                RecordTableModel.Column.of("Importe", expense -> Money.format(expense.amount())),
                RecordTableModel.Column.of("Descripcion", Expense::description)));
        JTable table = Ui.table(model);
        Runnable reload = () -> Async.run(() -> expenseService.listByTrip(trip.id()),
                model::setRows, failure -> Ui.failure(this, failure));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.scroll(table), BorderLayout.CENTER);
        panel.add(Ui.row(Ui.button("Nuevo gasto", () -> openExpenseForm(reload)),
                Ui.button("Recargar", reload)), BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    private void openExpenseForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addCombo("type", "Tipo", ExpenseType.values(), ExpenseType.TOLLS)
                .addText("amount", "Importe", "0")
                .addText("date", "Fecha (yyyy-MM-dd)", Dates.format(Dates.today()))
                .addText("description", "Descripcion", "");
        ModalForm.show(this, "Gasto del viaje " + trip.folio(), form, () -> {
            BigDecimal amount = Money.parse(form.text("amount")).orElse(BigDecimal.ZERO);
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            Expense expense = new Expense(0, trip.id(), trip.folio(),
                    (ExpenseType) form.selected("type"), amount, date, form.text("description"));
            return expenseService.register(expense);
        }, onSaved);
    }

    private JPanel fuelPanel() {
        RecordTableModel<FuelLoad> model = new RecordTableModel<>(java.util.List.of(
                RecordTableModel.Column.of("Fecha", load -> Dates.format(load.loadDate())),
                RecordTableModel.Column.of("Estacion", FuelLoad::fuelStation),
                RecordTableModel.Column.of("Litros", FuelLoad::liters),
                RecordTableModel.Column.of("Precio/L", FuelLoad::pricePerLiter),
                RecordTableModel.Column.of("Importe", load -> Money.format(load.amount())),
                RecordTableModel.Column.of("Odometro", FuelLoad::odometerReading)));
        JTable table = Ui.table(model);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.scroll(table), BorderLayout.CENTER);
        panel.add(Ui.row(Ui.button("Recargar", () -> Async.run(
                () -> fuelService.listByTrip(trip.id()), model::setRows,
                failure -> Ui.failure(this, failure)))), BorderLayout.SOUTH);
        Async.run(() -> fuelService.listByTrip(trip.id()), model::setRows,
                failure -> Ui.failure(this, failure));
        return panel;
    }

    private JPanel advancesPanel() {
        RecordTableModel<Advance> model = new RecordTableModel<>(java.util.List.of(
                RecordTableModel.Column.of("Operador", Advance::employeeName),
                RecordTableModel.Column.of("Monto", advance -> Money.format(advance.amountGiven())),
                RecordTableModel.Column.of("Fecha", advance -> Dates.format(advance.deliveredDate())),
                RecordTableModel.Column.of("Estado", advance -> advance.status().label())));
        JTable table = Ui.table(model);
        Advance[] cache = new Advance[1];
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            cache[0] = row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
        });
        Runnable reload = () -> Async.run(() -> advanceService.listByTrip(trip.id()),
                model::setRows, failure -> Ui.failure(this, failure));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.scroll(table), BorderLayout.CENTER);
        panel.add(Ui.row(
                Ui.button("Registrar anticipo", () -> openAdvanceForm(reload)),
                Ui.button("Comprobar", () -> {
                    if (cache[0] == null) {
                        Ui.info(this, "Seleccione un anticipo");
                        return;
                    }
                    var result = advanceService.settle(cache[0].id(), trip.id());
                    if (result.isErr()) {
                        Ui.error(this, "Error", result.problems());
                    } else {
                        reload.run();
                    }
                }),
                Ui.button("Recargar", reload)), BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    private void openAdvanceForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addText("amount", "Monto entregado", "0")
                .addText("date", "Fecha de entrega (yyyy-MM-dd)", Dates.format(Dates.today()));
        ModalForm.show(this, "Anticipo del viaje " + trip.folio(), form, () -> {
            BigDecimal amount = Money.parse(form.text("amount")).orElse(BigDecimal.ZERO);
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            Advance advance = new Advance(0, trip.id(), trip.folio(), trip.employeeId(),
                    trip.employeeName(), amount, date, mx.marjan.finance.AdvanceStatus.PENDING, null);
            return advanceService.register(advance);
        }, onSaved);
    }

    private JPanel incidentsPanel() {
        RecordTableModel<Incident> model = new RecordTableModel<>(java.util.List.of(
                RecordTableModel.Column.of("Fecha", incident -> Dates.format(incident.incidentDate())),
                RecordTableModel.Column.of("Tipo", incident -> incident.type().label()),
                RecordTableModel.Column.of("Ubicacion", Incident::location),
                RecordTableModel.Column.of("Descripcion", Incident::description),
                RecordTableModel.Column.of("Acciones", Incident::actionsTaken)));
        JTable table = Ui.table(model);
        Runnable reload = () -> Async.run(() -> incidentService.listByTrip(trip.id()),
                model::setRows, failure -> Ui.failure(this, failure));
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.scroll(table), BorderLayout.CENTER);
        panel.add(Ui.row(Ui.button("Nueva incidencia", () -> openIncidentForm(reload)),
                Ui.button("Recargar", reload)), BorderLayout.SOUTH);
        reload.run();
        return panel;
    }

    private void openIncidentForm(Runnable onSaved) {
        FormPanel form = new FormPanel()
                .addText("date", "Fecha (yyyy-MM-dd)", Dates.format(Dates.today()))
                .addText("time", "Hora (HH:mm)", "")
                .addCombo("type", "Tipo", IncidentType.values(), IncidentType.OTHER)
                .addText("location", "Ubicacion", "")
                .addArea("description", "Descripcion", "")
                .addArea("actions", "Acciones realizadas", "");
        ModalForm.show(this, "Incidencia del viaje " + trip.folio(), form, () -> {
            LocalDate date = Dates.parseDate(form.text("date")).orElse(null);
            java.time.LocalTime time = null;
            if (!form.text("time").isBlank()) {
                try {
                    time = java.time.LocalTime.parse(form.text("time"));
                } catch (RuntimeException failure) {
                    return Result.err("La hora debe tener el formato HH:mm");
                }
            }
            Incident incident = new Incident(0, trip.id(), trip.folio(), date, time,
                    form.text("location"), (IncidentType) form.selected("type"),
                    form.text("description"), form.text("actions"));
            return incidentService.register(incident);
        }, onSaved);
    }

    private JPanel deliveryPanel() {
        JLabel status = new JLabel("Cargando...");
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(Ui.titled("Entrega registrada", status), BorderLayout.CENTER);
        panel.add(Ui.row(Ui.button("Registrar / actualizar entrega", () -> openDeliveryForm()),
                Ui.button("Recargar", () -> loadDelivery(status))), BorderLayout.SOUTH);
        loadDelivery(status);
        return panel;
    }

    private void loadDelivery(JLabel status) {
        Async.run(() -> deliveryService.findByTrip(trip.id()), delivery -> {
            if (delivery.isEmpty()) {
                status.setText("Sin entrega registrada");
            } else {
                Delivery record = delivery.get();
                status.setText("<html>Fecha: " + Dates.format(record.actualDatetime())
                        + "<br>Recibio: " + record.receivedBy()
                        + "<br>Evidencia: " + record.evidenceReference()
                        + "<br>Estado: " + record.status().label() + "</html>");
            }
        }, failure -> status.setText("Error al cargar"));
    }

    private void openDeliveryForm() {
        FormPanel form = new FormPanel()
                .addText("datetime", "Fecha y hora (yyyy-MM-dd HH:mm)", Dates.format(Dates.now()))
                .addText("receivedBy", "Recibio", "")
                .addText("evidence", "Referencia de evidencia", "");
        ModalForm.show(this, "Entrega del viaje " + trip.folio(), form, () -> {
            java.time.LocalDateTime dateTime = Dates.parseDateTime(form.text("datetime")).orElse(null);
            if (dateTime == null) {
                return Result.err("La fecha y hora son obligatorias (yyyy-MM-dd HH:mm)");
            }
            Delivery delivery = new Delivery(0, trip.id(), trip.folio(), dateTime,
                    form.text("receivedBy"), form.text("evidence"), DeliveryStatus.COMPLETE);
            return deliveryService.register(delivery);
        });
    }
}
