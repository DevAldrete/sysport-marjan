package mx.marjan;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mx.marjan.security.LoginView;
import mx.marjan.security.Session;
import mx.marjan.shared.Database;

/** Entry point: connect, log in, open the main window. */
public final class App {

    private App() {}

    public static void main(String[] args) {
        installLookAndFeel();
        if (!Database.testConnection()) {
            JOptionPane.showMessageDialog(null,
                    "No se pudo conectar a la base de datos.\n\nURL: " + Database.url()
                            + "\n\nVerifique que MariaDB este corriendo (docker compose up -d).",
                    "SysPort MARJAN", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
        start();
    }

    /** Shows the login and, on success, the main window. Called again after logout. */
    public static void start() {
        SwingUtilities.invokeLater(() -> {
            var user = LoginView.prompt(null);
            if (user.isEmpty()) {
                System.exit(0);
            }
            Session.login(user.get());
            new MainFrame().setVisible(true);
        });
    }

    private static void installLookAndFeel() {
        try {
            com.formdev.flatlaf.FlatLightLaf.setup();
        } catch (Throwable ignored) {
            // FlatLaf is optional; fall back to the default look and feel.
        }
    }
}
