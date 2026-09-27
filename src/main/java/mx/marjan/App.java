package mx.marjan;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import mx.marjan.security.LoginView;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;
import mx.marjan.ui.AppShell;

/** Entry point: connect, log in, open the main window. */
public final class App extends Application {

    private boolean connected;

    @Override
    public void init() {
        connected = Database.testConnection();
    }

    @Override
    public void start(Stage primaryStage) {
        if (!connected) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "No se pudo conectar a la base de datos.\n\nURL: " + Database.url()
                            + "\n\nVerifique que MySQL este corriendo (docker compose up -d).",
                    ButtonType.OK);
            alert.setTitle("SysPort MARJAN");
            alert.setHeaderText("Sin conexion");
            alert.showAndWait();
            Platform.exit();
            return;
        }
        launchSession();
    }

    /** Shows the login and, on success, the main window. Called again after logout. */
    public static void launchSession() {
        var user = LoginView.prompt();
        if (user.isEmpty()) {
            Platform.exit();
            return;
        }
        Session.login(user.get());
        new AppShell().show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
