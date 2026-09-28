package mx.marjan.security;

import java.util.Optional;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import mx.marjan.ui.Async;
import mx.marjan.ui.ThemeManager;
import org.kordamp.ikonli.feather.Feather;

/** Modal login screen. Returns the authenticated CurrentUser or nothing. */
public final class LoginView {

    private final AuthService authService = new AuthService();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final Label messageLabel = new Label(" ");
    private final Button loginButton = new Button("Entrar");

    private CurrentUser user;

    private LoginView() {}

    public static Optional<CurrentUser> prompt() {
        LoginView view = new LoginView();
        return Optional.ofNullable(view.show());
    }

    private CurrentUser show() {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("SysPort MARJAN - Acceso");
        stage.setResizable(false);

        Label title = new Label("SysPort");
        title.getStyleClass().add("placeholder-title");
        Label subtitle = new Label("Transportes MARJAN");
        subtitle.getStyleClass().add("brand-subtitle");

        usernameField.setPromptText("Usuario");
        passwordField.setPromptText("Contrasena");
        usernameField.setPrefColumnCount(18);
        passwordField.setPrefColumnCount(18);

        messageLabel.getStyleClass().add("form-error");
        messageLabel.setWrapText(true);
        messageLabel.setMinHeight(18);

        loginButton.setDefaultButton(true);
        loginButton.getStyleClass().add("accent");
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setGraphic(mx.marjan.ui.Icons.icon(Feather.LOG_IN, 15));
        loginButton.setOnAction(event -> attemptLogin(stage));
        passwordField.setOnAction(event -> attemptLogin(stage));

        VBox card = new VBox(8, title, subtitle, spacer(6), fieldLabel("Usuario"), usernameField,
                fieldLabel("Contrasena"), passwordField, messageLabel, loginButton);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(24, 24, 24, 24));
        card.setPrefWidth(340);
        card.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(card);
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("app-shell");

        Scene scene = new Scene(root);
        ThemeManager.apply(scene);
        stage.setScene(scene);
        stage.setOnShown(event -> usernameField.requestFocus());
        stage.showAndWait();
        return user;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-hint");
        return label;
    }

    private javafx.scene.Node spacer(double height) {
        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        region.setPrefHeight(height);
        return region;
    }

    private void attemptLogin(Stage stage) {
        String username = usernameField.getText();
        String password = passwordField.getText();
        loginButton.setDisable(true);
        messageLabel.setText("Verificando...");
        Async.run(() -> authService.login(username, password),
                result -> {
                    loginButton.setDisable(false);
                    if (result.isOk()) {
                        user = result.value();
                        stage.close();
                    } else {
                        messageLabel.setText(String.join(" ", result.problems()));
                    }
                },
                failure -> {
                    loginButton.setDisable(false);
                    messageLabel.setText("Error de conexion: " + failure.getMessage());
                });
    }
}
