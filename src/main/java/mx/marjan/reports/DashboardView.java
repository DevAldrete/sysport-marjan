package mx.marjan.reports;

import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import mx.marjan.security.Session;
import mx.marjan.ui.Async;
import mx.marjan.shared.Dates;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.Icons;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class DashboardView extends BaseView {

    private final DashboardService service = new DashboardService();
    private final Consumer<String> navigate;
    private final Label licenses = value();
    private final Label invoices = value();
    private final Label maintenance = value();
    private final Label pending = value();

    public DashboardView(Consumer<String> navigate) {
        this.navigate = navigate;
        Label welcome = new Label("Bienvenido, " + Session.user().username()
                + " (" + Session.user().roleName() + ")");
        welcome.getStyleClass().add("placeholder-text");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.add(card("Licencias por vencer", licenses, "Operadores", "Revise las licencias proximas a vencer"), 0, 0);
        grid.add(card("Facturas vencidas", invoices, "Facturas", "Cobre los saldos pendientes"), 1, 0);
        grid.add(card("Unidades con mantenimiento proximo", maintenance, "Unidades", "Programe el servicio"), 0, 1);
        grid.add(card("Solicitudes por asignar", pending, "Solicitudes", "Asigne unidad y operador"), 1, 1);

        var actualizar = Ui.button("Actualizar", this::reload);
        actualizar.setGraphic(Icons.action(Feather.REFRESH_CW));
        var header = Ui.toolbar(welcome, actualizar);
        setTop(new VBox(header));
        setCenter(grid);
        reload();
    }

    @Override
    public void reload() {
        Async.run(() -> service.alerts(Dates.today()), alerts -> {
            licenses.setText(String.valueOf(alerts.expiringLicenses()));
            invoices.setText(String.valueOf(alerts.overdueInvoices()));
            maintenance.setText(String.valueOf(alerts.maintenanceDue()));
            pending.setText(String.valueOf(alerts.pendingAssignments()));
        }, failure -> Ui.failure(Ui.windowOf(this), failure));
    }

    private Node card(String title, Label value, String target, String hint) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("kpi-label");
        titleLabel.setWrapText(true);
        Label hintLabel = new Label(hint);
        hintLabel.getStyleClass().add("kpi-hint");
        VBox box = new VBox(6, titleLabel, value, hintLabel);
        box.getStyleClass().addAll("card", "kpi-card");
        box.setPadding(new Insets(18));
        box.setAlignment(Pos.CENTER_LEFT);
        GridPane.setHgrow(box, Priority.ALWAYS);
        if (navigate != null) {
            box.setOnMouseClicked(event -> navigate.accept(target));
        }
        return box;
    }

    private static Label value() {
        Label label = new Label("...");
        label.getStyleClass().add("kpi-value");
        return label;
    }
}
