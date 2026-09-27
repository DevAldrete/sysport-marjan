package mx.marjan.ui;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import mx.marjan.App;
import mx.marjan.security.AuthService;
import mx.marjan.security.Permissions;
import mx.marjan.security.Session;
import org.kordamp.ikonli.feather.Feather;

/** The main window: a permission-aware sidebar, an app bar and a content area. */
public final class AppShell {

    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final Map<Navigation.Item, Node> screens = new HashMap<>();

    private Navigation navigation;
    private Stage stage;
    private Scene scene;
    private Button themeButton;

    public void show() {
        navigation = new Navigation(navItems());
        navigation.setOnSelect(this::open);

        content.getStyleClass().add("content-area");
        root.getStyleClass().add("app-shell");
        root.setLeft(navigation);
        root.setTop(appBar());
        root.setCenter(content);

        scene = new Scene(root, 1200, 760);
        ThemeManager.apply(scene);

        stage = new Stage();
        stage.setTitle("SysPort - Transportes MARJAN");
        stage.setScene(scene);
        stage.setMinWidth(980);
        stage.setMinHeight(640);
        stage.show();

        Navigation.Item first = navigation.first();
        if (first != null) {
            navigation.select(first);
            open(first);
        }
    }

    private HBox appBar() {
        Button collapse = new Button(null, Icons.icon(Feather.MENU, 16));
        collapse.getStyleClass().add("flat");
        collapse.setTooltip(new javafx.scene.control.Tooltip("Mostrar u ocultar el menu"));
        collapse.setOnAction(event -> navigation.setCollapsed(!navigation.isCollapsed()));

        titleLabel.getStyleClass().add("app-title");
        subtitleLabel.getStyleClass().add("app-subtitle");
        VBox titles = new VBox(1, titleLabel, subtitleLabel);
        titles.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        themeButton = new Button(null, Icons.icon(Feather.MOON, 16));
        themeButton.getStyleClass().add("flat");
        themeButton.setTooltip(new javafx.scene.control.Tooltip("Cambiar tema claro/oscuro"));
        themeButton.setOnAction(event -> toggleTheme());

        MenuButton userMenu = new MenuButton(Session.user().username(), Icons.icon(Feather.USER, 15));
        userMenu.getStyleClass().add("flat");
        MenuItem changePassword = new MenuItem("Cambiar contrasena");
        changePassword.setOnAction(event -> changePassword());
        MenuItem logout = new MenuItem("Cerrar sesion");
        logout.setOnAction(event -> logout());
        userMenu.getItems().addAll(changePassword, logout);

        HBox bar = new HBox(10, collapse, titles, spacer, themeButton, userMenu);
        bar.getStyleClass().add("app-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(10, 16, 10, 12));
        return bar;
    }

    private void open(Navigation.Item item) {
        titleLabel.setText(item.title());
        subtitleLabel.setText(Session.user().roleName());
        Node screen = screens.computeIfAbsent(item, key -> key.screen().get());
        content.getChildren().setAll(screen);
        if (screen instanceof BaseView base) {
            base.reload();
        }
    }

    private void toggleTheme() {
        ThemeManager.toggle();
        ThemeManager.apply(scene);
        updateThemeButton();
    }

    private void updateThemeButton() {
        if (themeButton != null) {
            themeButton.setGraphic(Icons.icon(
                    ThemeManager.mode() == ThemeManager.Mode.DARK ? Feather.SUN : Feather.MOON, 16));
        }
    }

    private void changePassword() {
        FormPanel form = new FormPanel()
                .addPassword("current", "Contrasena actual")
                .addPassword("new", "Contrasena nueva");
        ModalForm.show(stage, "Cambiar contrasena", form,
                () -> new AuthService().changeOwnPassword(form.text("current"), form.text("new")));
    }

    private void logout() {
        if (!Ui.confirm(stage, "\u00bfCerrar sesion?")) {
            return;
        }
        stage.close();
        Session.logout();
        App.launchSession();
    }

    private List<Navigation.Item> navItems() {
        return List.of(
                item("Operacion", "Inicio", Feather.HOME, null,
                        () -> new PlaceholderScreen("Inicio")),
                item("Operacion", "Solicitudes", Feather.CLIPBOARD, Permissions.REQUESTS_READ,
                        () -> new PlaceholderScreen("Solicitudes")),
                item("Operacion", "Viajes", Feather.TRUCK, Permissions.TRIPS_READ,
                        () -> new PlaceholderScreen("Viajes")),
                item("Catalogos", "Clientes", Feather.USERS, Permissions.CLIENTS_READ,
                        mx.marjan.clients.ClientsView::new),
                item("Catalogos", "Rutas", Feather.MAP, Permissions.ROUTES_READ,
                        mx.marjan.routes.RoutesView::new),
                item("Catalogos", "Unidades", Feather.BOX, Permissions.FLEET_READ,
                        mx.marjan.fleet.VehiclesView::new),
                item("Catalogos", "Operadores", Feather.USER, Permissions.OPERATORS_READ,
                        mx.marjan.operators.OperatorsView::new),
                item("Catalogos", "Combustible", Feather.DROPLET, Permissions.FUEL_READ,
                        mx.marjan.fleet.FuelLoadsView::new),
                item("Finanzas", "Facturas", Feather.DOLLAR_SIGN, Permissions.INVOICES_READ,
                        () -> new PlaceholderScreen("Facturas")),
                item("Finanzas", "Reportes", Feather.BAR_CHART_2, Permissions.REPORTS_VIEW,
                        () -> new PlaceholderScreen("Reportes")),
                item("Sistema", "Usuarios", Feather.SHIELD, Permissions.SECURITY_USERS,
                        () -> new PlaceholderScreen("Usuarios")));
    }

    private static Navigation.Item item(String group, String title, Feather icon, String permission,
            java.util.function.Supplier<Node> screen) {
        return new Navigation.Item(group, title, icon, permission, screen);
    }
}
