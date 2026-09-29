package mx.marjan.reports;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Cards;
import mx.marjan.shared.Charts;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Icons;
import mx.marjan.shared.KpiCard;
import mx.marjan.shared.Money;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Theme;
import mx.marjan.shared.Ui;

/**
 * FR-DSH-1..6: the home dashboard. Cards, charts and lists are shown only when
 * the user has the permission for the underlying data, and every card/row links
 * to the tab where the work happens.
 */
public class DashboardView extends BaseView {

    private static final int KPI_COLUMNS = 4;
    private static final int CHART_MONTHS = 12;
    private static final int DEBTOR_LIMIT = 10;
    private static final int UPCOMING_DAYS = 7;
    private static final DateTimeFormatter UPDATED_AT = DateTimeFormatter.ofPattern("HH:mm");

    private final Consumer<String> navigator;
    private final DashboardService service = new DashboardService();

    private final JLabel updated = new JLabel(" ");

    private KpiCard cardPending;
    private KpiCard cardOverdueInvoices;
    private KpiCard cardLicenses;
    private KpiCard cardMaintenance;
    private KpiCard cardRevenue;
    private KpiCard cardReceivable;
    private KpiCard cardActiveTrips;
    private KpiCard cardAvailable;

    private final RecordTableModel<UpcomingTrip> upcomingModel = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Folio", UpcomingTrip::folio),
            RecordTableModel.Column.of("Cliente", UpcomingTrip::clientName),
            RecordTableModel.Column.text("Ruta", UpcomingTrip::routeLabel, 32),
            RecordTableModel.Column.text("Unidad", UpcomingTrip::vehicleLabel, 22),
            RecordTableModel.Column.text("Operador", UpcomingTrip::operatorName, 22),
            RecordTableModel.Column.of("Salida", trip -> Dates.format(trip.plannedStart())),
            RecordTableModel.Column.of("Estado", trip -> trip.status().label())));
    private final JTable upcomingTable = Ui.table(upcomingModel);

    private final RecordTableModel<Debtor> debtorModel = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Cliente", Debtor::clientName),
            RecordTableModel.Column.of("Saldo", debtor -> Money.format(debtor.balance())),
            RecordTableModel.Column.of("Facturas", Debtor::invoices),
            RecordTableModel.Column.of("Vence", debtor -> Dates.format(debtor.oldestDue()))));
    private final JTable debtorTable = Ui.table(debtorModel);

    private final JPanel revenueHost = new JPanel(new BorderLayout());
    private final JPanel fleetHost = new JPanel(new BorderLayout());

    private int loading;

    public DashboardView(Consumer<String> navigator) {
        this.navigator = navigator;
        setBackground(Theme.BACKGROUND);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(4, 4, 8, 4));

        addBlock(content, header());
        addBlock(content, kpiGrid());
        addBlock(content, quickActions());
        addBlock(content, charts());
        addBlock(content, lists());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(Theme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        reload();
    }

    @Override
    public void reload() {
        loading = 0;
        updated.setText("Actualizando...");
        reloadAlerts();
        reloadOperations();
        reloadFinance();
        reloadUpcoming();
        reloadDebtors();
        reloadRevenueChart();
        reloadFleetChart();
        if (loading == 0) {
            loaded();
        }
    }

    private <T> void dashboardLoad(Callable<T> task, Consumer<T> onSuccess) {
        loading++;
        load(task, value -> {
            try {
                onSuccess.accept(value);
            } finally {
                loaded();
            }
        }, failure -> {
            loaded();
            Ui.failure(this, failure);
        });
    }

    private void loaded() {
        loading--;
        if (loading <= 0) {
            loading = 0;
            updated.setText("Actualizado " + UPDATED_AT.format(LocalDateTime.now()));
        }
    }

    // ----------------------------------------------------------------- blocks

    private JComponent header() {
        JLabel greeting = new JLabel("Bienvenido, " + Session.user().username());
        greeting.setFont(Theme.bold(20f));
        greeting.setForeground(Theme.TEXT);

        JLabel role = new JLabel(Session.user().roleName());
        role.setFont(Theme.subtitle());
        role.setForeground(Theme.MUTED);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        greeting.setAlignmentX(Component.LEFT_ALIGNMENT);
        role.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(greeting);
        titles.add(Box.createVerticalStrut(2));
        titles.add(role);

        updated.setFont(Theme.subtitle());
        updated.setForeground(Theme.MUTED);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.X_AXIS));
        controls.add(updated);
        controls.add(Box.createHorizontalStrut(12));
        controls.add(Ui.button("Actualizar", "Recargar el panel",
                Icons.refresh(16, Theme.PRIMARY), this::reload));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(6, 6, 14, 6));
        header.add(titles, BorderLayout.WEST);
        header.add(controls, BorderLayout.EAST);
        return header;
    }

    private JComponent kpiGrid() {
        java.util.List<KpiCard> cards = new java.util.ArrayList<>();

        cardPending = new KpiCard("Solicitudes por asignar", Icons.request(24, Theme.WARNING), Theme.WARNING);
        cardPending.tooltip("Solicitudes programadas sin viaje asignado");
        if (Session.has(Permissions.REQUESTS_READ)) {
            cardPending.onClick(() -> navigator.accept("Solicitudes"));
        }
        cards.add(cardPending);

        cardOverdueInvoices = new KpiCard("Facturas vencidas", Icons.invoice(24, Theme.DANGER), Theme.DANGER);
        cardOverdueInvoices.tooltip("Facturas sin pagar pasada su fecha de vencimiento");
        if (Session.has(Permissions.INVOICES_READ)) {
            cardOverdueInvoices.onClick(() -> navigator.accept("Facturas"));
        }
        cards.add(cardOverdueInvoices);

        cardLicenses = new KpiCard("Licencias por vencer", Icons.license(24, Theme.WARNING), Theme.WARNING);
        cardLicenses.tooltip("Licencias que vencen en los proximos 30 dias");
        if (Session.has(Permissions.OPERATORS_READ)) {
            cardLicenses.onClick(() -> navigator.accept("Operadores"));
        }
        cards.add(cardLicenses);

        cardMaintenance = new KpiCard("Mantenimiento proximo", Icons.maintenance(24, Theme.INFO), Theme.INFO);
        cardMaintenance.tooltip("Unidades con servicio por fecha o kilometraje");
        if (Session.has(Permissions.FLEET_READ)) {
            cardMaintenance.onClick(() -> navigator.accept("Unidades"));
        }
        cards.add(cardMaintenance);

        if (Session.has(Permissions.INVOICES_READ)) {
            cardRevenue = new KpiCard("Ingresos del mes", Icons.money(24, Theme.SUCCESS), Theme.SUCCESS);
            cardRevenue.onClick(() -> navigator.accept("Facturas"));
            cards.add(cardRevenue);

            cardReceivable = new KpiCard("Por cobrar", Icons.coins(24, Theme.PRIMARY), Theme.PRIMARY);
            cardReceivable.onClick(() -> navigator.accept("Facturas"));
            cards.add(cardReceivable);
        }

        boolean operations = Session.has(Permissions.TRIPS_READ) || Session.has(Permissions.FLEET_READ);
        if (operations) {
            cardActiveTrips = new KpiCard("Viajes activos", Icons.route(24, Theme.PURPLE), Theme.PURPLE);
            if (Session.has(Permissions.TRIPS_READ)) {
                cardActiveTrips.onClick(() -> navigator.accept("Viajes"));
            }
            cards.add(cardActiveTrips);

            cardAvailable = new KpiCard("Unidades disponibles", Icons.truck(24, Theme.SUCCESS), Theme.SUCCESS);
            if (Session.has(Permissions.FLEET_READ)) {
                cardAvailable.onClick(() -> navigator.accept("Unidades"));
            }
            cards.add(cardAvailable);
        }

        int rows = (cards.size() + KPI_COLUMNS - 1) / KPI_COLUMNS;
        JPanel grid = new JPanel(new GridLayout(rows, KPI_COLUMNS, 14, 14));
        grid.setOpaque(false);
        for (KpiCard card : cards) {
            grid.add(card);
        }
        for (int i = cards.size(); i < rows * KPI_COLUMNS; i++) {
            grid.add(transparent());
        }
        fixHeight(grid, rows * 84 + (rows - 1) * 14);
        return grid;
    }

    private JComponent quickActions() {
        java.util.List<JComponent> actions = new java.util.ArrayList<>();
        if (Session.has(Permissions.REQUESTS_WRITE)) {
            actions.add(Ui.button("Nueva solicitud", "Ir al modulo de solicitudes",
                    Icons.add(16, Theme.PRIMARY), () -> navigator.accept("Solicitudes")));
        }
        if (Session.has(Permissions.TRIPS_ASSIGN)) {
            actions.add(Ui.button("Asignar viaje", "Ir al modulo de viajes",
                    Icons.truck(16, Theme.PRIMARY), () -> navigator.accept("Viajes")));
        }
        if (Session.has(Permissions.INVOICES_WRITE)) {
            actions.add(Ui.button("Facturar", "Ir al modulo de facturas",
                    Icons.invoice(16, Theme.PRIMARY), () -> navigator.accept("Facturas")));
        }
        if (Session.has(Permissions.PAYMENTS_WRITE)) {
            actions.add(Ui.button("Registrar pago", "Ir al modulo de facturas",
                    Icons.payment(16, Theme.PRIMARY), () -> navigator.accept("Facturas")));
        }
        if (Session.has(Permissions.REPORTS_VIEW)) {
            actions.add(Ui.button("Ver reportes", "Abrir los reportes",
                    Icons.chart(16, Theme.PRIMARY), () -> navigator.accept("Reportes")));
        }
        if (actions.isEmpty()) {
            return transparent();
        }
        actions.add(0, heading("Accesos rapidos"));
        return row(actions.toArray(JComponent[]::new));
    }

    private JComponent charts() {
        java.util.List<JComponent> sections = new java.util.ArrayList<>();
        if (Session.has(Permissions.REPORTS_VIEW)) {
            revenueHost.setOpaque(false);
            sections.add(Cards.section("Ingresos y margen (12 meses)",
                    Icons.chart(16, Theme.MUTED), revenueHost));
        }
        if (Session.has(Permissions.FLEET_READ)) {
            fleetHost.setOpaque(false);
            sections.add(Cards.section("Unidades por estatus",
                    Icons.truck(16, Theme.MUTED), fleetHost));
        }
        if (sections.isEmpty()) {
            return transparent();
        }
        JPanel grid = new JPanel(new GridLayout(1, sections.size(), 14, 14));
        grid.setOpaque(false);
        for (JComponent section : sections) {
            fixHeight((JComponent) section, 250);
            grid.add(section);
        }
        fixHeight(grid, 250);
        return grid;
    }

    private JComponent lists() {
        java.util.List<JComponent> sections = new java.util.ArrayList<>();
        if (Session.has(Permissions.TRIPS_READ)) {
            Ui.onDoubleClick(upcomingTable, () -> navigator.accept("Viajes"));
            upcomingTable.setFillsViewportHeight(true);
            JScrollPane scroll = Ui.scroll(upcomingTable);
            scroll.setPreferredSize(new Dimension(0, 190));
            sections.add(Cards.section("Proximos viajes",
                    Icons.route(16, Theme.MUTED), scroll));
        }
        if (Session.has(Permissions.INVOICES_READ)) {
            Ui.onDoubleClick(debtorTable, () -> navigator.accept("Facturas"));
            debtorTable.setFillsViewportHeight(true);
            JScrollPane scroll = Ui.scroll(debtorTable);
            scroll.setPreferredSize(new Dimension(0, 190));
            sections.add(Cards.section("Saldos por cobrar",
                    Icons.coins(16, Theme.MUTED), scroll));
        }
        if (sections.isEmpty()) {
            return transparent();
        }
        JPanel grid = new JPanel(new GridLayout(1, sections.size(), 14, 14));
        grid.setOpaque(false);
        for (JComponent section : sections) {
            fixHeight((JComponent) section, 240);
            grid.add(section);
        }
        fixHeight(grid, 240);
        return grid;
    }

    // ------------------------------------------------------------------ loads

    private void reloadAlerts() {
        dashboardLoad(() -> service.alerts(Dates.today()), alerts -> {
            cardPending.setValue(String.valueOf(alerts.pendingAssignments()));
            cardOverdueInvoices.setValue(String.valueOf(alerts.overdueInvoices()));
            cardLicenses.setValue(String.valueOf(alerts.expiringLicenses()));
            cardMaintenance.setValue(String.valueOf(alerts.maintenanceDue()));
        });
    }

    private void reloadOperations() {
        if (cardActiveTrips == null) {
            return;
        }
        dashboardLoad(() -> service.operations(Dates.today()), result -> {
            if (result.isOk()) {
                cardActiveTrips.setValue(String.valueOf(result.value().activeTrips()));
                cardAvailable.setValue(String.valueOf(result.value().availableVehicles()));
            } else {
                cardActiveTrips.setValue("-");
                cardAvailable.setValue("-");
            }
        });
    }

    private void reloadFinance() {
        if (cardRevenue == null) {
            return;
        }
        dashboardLoad(() -> service.finance(Dates.today()), result -> {
            if (result.isOk()) {
                DashboardFinance finance = result.value();
                cardRevenue.setValue(compact(finance.monthRevenue()));
                cardRevenue.tooltip(Money.format(finance.monthRevenue()));
                cardReceivable.setValue(compact(finance.receivable()));
                cardReceivable.tooltip(Money.format(finance.receivable())
                        + " por cobrar · " + Money.format(finance.overdue()) + " vencido");
            } else {
                cardRevenue.setValue("-");
                cardReceivable.setValue("-");
            }
        });
    }

    private void reloadUpcoming() {
        if (!Session.has(Permissions.TRIPS_READ)) {
            return;
        }
        dashboardLoad(() -> service.upcomingTrips(Dates.today(), UPCOMING_DAYS), result -> {
            if (result.isOk()) {
                upcomingModel.setRows(result.value());
            } else {
                Ui.error(this, "No se pudieron cargar los proximos viajes", result.problems());
            }
        });
    }

    private void reloadDebtors() {
        if (!Session.has(Permissions.INVOICES_READ)) {
            return;
        }
        dashboardLoad(() -> service.topDebtors(DEBTOR_LIMIT), result -> {
            if (result.isOk()) {
                debtorModel.setRows(result.value());
            } else {
                Ui.error(this, "No se pudieron cargar los saldos", result.problems());
            }
        });
    }

    private void reloadRevenueChart() {
        if (!Session.has(Permissions.REPORTS_VIEW)) {
            return;
        }
        dashboardLoad(() -> service.monthlyRevenue(CHART_MONTHS), result -> {
            if (result.isErr()) {
                showPlaceholder(revenueHost, result.problems().get(0));
                return;
            }
            List<MonthlyRevenue> data = result.value();
            if (data.isEmpty()) {
                showPlaceholder(revenueHost, "Sin datos de ingresos");
                return;
            }
            String[] months = data.stream().map(MonthlyRevenue::month).toArray(String[]::new);
            double[] revenue = data.stream().mapToDouble(row -> decimal(row.revenue())).toArray();
            double[] margin = data.stream().mapToDouble(row -> decimal(row.margin())).toArray();
            replace(revenueHost, Charts.barLine("Monto", months,
                    revenue, "Ingresos", Theme.SUCCESS, margin, "Margen", Theme.PRIMARY));
        });
    }

    private void reloadFleetChart() {
        if (!Session.has(Permissions.FLEET_READ)) {
            return;
        }
        dashboardLoad(service::fleetStatus, result -> {
            if (result.isErr()) {
                showPlaceholder(fleetHost, result.problems().get(0));
                return;
            }
            List<FleetStatusCount> data = result.value().stream()
                    .filter(row -> row.count() > 0)
                    .toList();
            if (data.isEmpty()) {
                showPlaceholder(fleetHost, "Sin unidades registradas");
                return;
            }
            String[] keys = data.stream().map(row -> row.status().label()).toArray(String[]::new);
            double[] values = data.stream().mapToDouble(FleetStatusCount::count).toArray();
            Color[] palette = {
                Theme.SUCCESS, Theme.PRIMARY, Theme.INFO, Theme.WARNING, Theme.DANGER, Theme.MUTED
            };
            replace(fleetHost, Charts.pie(keys, values, palette));
        });
    }

    // ---------------------------------------------------------------- helpers

    private void addBlock(JPanel content, JComponent block) {
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(block);
        content.add(Box.createVerticalStrut(14));
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Theme.section());
        label.setForeground(Theme.MUTED);
        return label;
    }

    private static JPanel row(Component... components) {
        JPanel panel = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 6));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        for (Component component : components) {
            panel.add(component);
        }
        return panel;
    }

    private static JPanel transparent() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        return panel;
    }

    private static void fixHeight(JComponent component, int height) {
        component.setPreferredSize(new Dimension(0, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setMinimumSize(new Dimension(0, height));
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static void replace(JPanel host, JComponent component) {
        host.removeAll();
        host.add(component, BorderLayout.CENTER);
        host.revalidate();
        host.repaint();
    }

    private static void showPlaceholder(JPanel host, String message) {
        JLabel label = new JLabel(message, JLabel.CENTER);
        label.setForeground(Theme.MUTED);
        label.setFont(Theme.subtitle());
        replace(host, label);
    }

    private static double decimal(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private static String compact(BigDecimal value) {
        BigDecimal amount = Money.zeroIfNull(value);
        BigDecimal abs = amount.abs();
        if (abs.compareTo(new BigDecimal("1000000")) >= 0) {
            return "$" + amount.divide(new BigDecimal("1000000"), 1, RoundingMode.HALF_UP) + "M";
        }
        if (abs.compareTo(new BigDecimal("10000")) >= 0) {
            return "$" + amount.divide(new BigDecimal("1000"), 0, RoundingMode.HALF_UP) + "k";
        }
        return Money.format(amount);
    }
}
