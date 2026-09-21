package mx.marjan.reports;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import mx.marjan.security.Session;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Ui;

public class DashboardView extends BaseView {

    private final DashboardService service = new DashboardService();
    private final JLabel licenses = counter();
    private final JLabel invoices = counter();
    private final JLabel maintenance = counter();
    private final JLabel pending = counter();

    public DashboardView() {
        JPanel grid = new JPanel(new GridLayout(2, 2, 12, 12));
        grid.add(card("Licencias por vencer", licenses));
        grid.add(card("Facturas vencidas", invoices));
        grid.add(card("Unidades con mantenimiento proximo", maintenance));
        grid.add(card("Solicitudes por asignar", pending));

        add(Ui.row(new JLabel("Bienvenido, " + Session.user().username()
                + " (" + Session.user().roleName() + ")"),
                Ui.button("Actualizar", this::reload)), BorderLayout.NORTH);
        add(grid, BorderLayout.CENTER);
        reload();
    }

    @Override
    public void reload() {
        Async.run(() -> service.alerts(Dates.today()), alerts -> {
            licenses.setText(String.valueOf(alerts.expiringLicenses()));
            invoices.setText(String.valueOf(alerts.overdueInvoices()));
            maintenance.setText(String.valueOf(alerts.maintenanceDue()));
            pending.setText(String.valueOf(alerts.pendingAssignments()));
        }, failure -> Ui.failure(this, failure));
    }

    private static JLabel counter() {
        JLabel label = new JLabel("...");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 28f));
        return label;
    }

    private static JPanel card(String title, JLabel value) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        panel.add(new JLabel(title), BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }
}
