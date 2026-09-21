package mx.marjan;

import java.awt.BorderLayout;
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
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;
import mx.marjan.trips.TripsView;

/** Main window: a tab per feature, shown only when the user has permission. */
public class MainFrame extends JFrame {

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
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Inicio", new DashboardView());
        addTab(tabs, Permissions.CLIENTS_READ, "Clientes", ClientsView::new);
        addTab(tabs, Permissions.ROUTES_READ, "Rutas", RoutesView::new);
        addTab(tabs, Permissions.OPERATORS_READ, "Operadores", OperatorsView::new);
        addTab(tabs, Permissions.FLEET_READ, "Unidades", VehiclesView::new);
        addTab(tabs, Permissions.FUEL_READ, "Combustible", FuelLoadsView::new);
        addTab(tabs, Permissions.REQUESTS_READ, "Solicitudes", ServiceRequestsView::new);
        addTab(tabs, Permissions.TRIPS_READ, "Viajes", TripsView::new);
        addTab(tabs, Permissions.INVOICES_READ, "Facturas", InvoicesView::new);
        addTab(tabs, Permissions.REPORTS_VIEW, "Reportes", ReportsView::new);
        addTab(tabs, Permissions.SECURITY_USERS, "Usuarios", UsersView::new);
        return tabs;
    }

    private void addTab(JTabbedPane tabs, String permission, String title,
            java.util.function.Supplier<javax.swing.JComponent> factory) {
        if (Session.has(permission)) {
            tabs.addTab(title, factory.get());
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
