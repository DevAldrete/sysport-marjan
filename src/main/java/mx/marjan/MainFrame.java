package mx.marjan;

import java.awt.BorderLayout;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JTabbedPane;
import mx.marjan.clients.ClientsView;
import mx.marjan.finance.InvoicesView;
import mx.marjan.fleet.FuelLoadsView;
import mx.marjan.fleet.VehiclesView;
import mx.marjan.operators.OperatorsView;
import mx.marjan.reports.DashboardView;
import mx.marjan.reports.ReportsView;
import mx.marjan.requests.ServiceRequestsView;
import mx.marjan.routes.RoutesView;
import mx.marjan.security.AuthService;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import mx.marjan.security.UsersView;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.Icons;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Theme;
import mx.marjan.trips.TripsView;

/** Main window: a tab per feature, shown only when the user has permission. */
public class MainFrame extends JFrame {

    private final JTabbedPane tabs = new JTabbedPane();

    public MainFrame() {
        super("SysPort - Transportes MARJAN");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(buildTabs(), BorderLayout.CENTER);
        setJMenuBar(buildMenu());
        setSize(1150, 720);
        setLocationRelativeTo(null);
    }

    private JTabbedPane buildTabs() {
        tabs.addTab("Inicio", Icons.home(16, Theme.PRIMARY), new DashboardView(this::navigate));
        addTab(Permissions.CLIENTS_READ, "Clientes", Icons.user(16, Theme.PRIMARY), ClientsView::new);
        addTab(Permissions.ROUTES_READ, "Rutas", Icons.route(16, Theme.PRIMARY), RoutesView::new);
        addTab(Permissions.OPERATORS_READ, "Operadores", Icons.license(16, Theme.PRIMARY), OperatorsView::new);
        addTab(Permissions.FLEET_READ, "Unidades", Icons.car(16, Theme.PRIMARY), VehiclesView::new);
        addTab(Permissions.FUEL_READ, "Combustible", Icons.fuel(16, Theme.PRIMARY), FuelLoadsView::new);
        addTab(Permissions.REQUESTS_READ, "Solicitudes", Icons.request(16, Theme.PRIMARY), ServiceRequestsView::new);
        addTab(Permissions.TRIPS_READ, "Viajes", Icons.truck(16, Theme.PRIMARY), TripsView::new);
        addTab(Permissions.INVOICES_READ, "Facturas", Icons.invoice(16, Theme.PRIMARY), InvoicesView::new);
        addTab(Permissions.REPORTS_VIEW, "Reportes", Icons.chart(16, Theme.PRIMARY), ReportsView::new);
        addTab(Permissions.SECURITY_USERS, "Usuarios", Icons.user(16, Theme.PRIMARY), UsersView::new);
        return tabs;
    }

    /** Selects the tab with the given title. Used by the dashboard shortcuts. */
    public void navigate(String title) {
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTitleAt(i).equals(title)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private void addTab(String permission, String title, Icon icon,
            java.util.function.Supplier<javax.swing.JComponent> factory) {
        if (Session.has(permission)) {
            tabs.addTab(title, icon, factory.get());
        }
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu session = new JMenu("Sesion");
        JMenuItem changePassword = new JMenuItem("Cambiar contrasena");
        changePassword.addActionListener(event -> changePassword());
        JMenuItem logout = new JMenuItem("Cerrar sesion");
        logout.addActionListener(event -> logout());
        JMenuItem exit = new JMenuItem("Salir");
        exit.addActionListener(event -> System.exit(0));
        session.add(changePassword);
        session.addSeparator();
        session.add(logout);
        session.add(exit);
        bar.add(session);
        return bar;
    }

    private void changePassword() {
        FormPanel form = new FormPanel()
                .addPassword("current", "Contrasena actual")
                .addPassword("new", "Contrasena nueva");
        ModalForm.show(this, "Cambiar contrasena", form,
                () -> new AuthService().changeOwnPassword(form.text("current"), form.text("new")));
    }

    private void logout() {
        if (JOptionPane.showConfirmDialog(this, "Cerrar sesion?", "Confirmar",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        dispose();
        Session.logout();
        App.start();
    }
}
