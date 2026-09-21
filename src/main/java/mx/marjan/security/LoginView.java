package mx.marjan.security;

import java.awt.Window;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import mx.marjan.shared.Async;

/** Modal login screen. Returns the authenticated CurrentUser or nothing. */
public class LoginView extends JDialog {

    private final AuthService authService = new AuthService();
    private final JTextField usernameField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JLabel messageLabel = new JLabel(" ");
    private final JButton loginButton = new JButton("Entrar");

    private CurrentUser user;

    private LoginView(Window owner) {
        super(owner, "SysPort MARJAN - Acceso", ModalityType.APPLICATION_MODAL);
        build();
        pack();
        setLocationRelativeTo(owner);
    }

    public static Optional<CurrentUser> prompt(Window owner) {
        LoginView view = new LoginView(owner);
        view.setVisible(true);
        return Optional.ofNullable(view.user);
    }

    private void build() {
        setResizable(false);
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel("SysPort - Transportes MARJAN");
        title.setAlignmentX(CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(18f));
        content.add(title);
        content.add(Box.createVerticalStrut(12));

        content.add(labelled("Usuario", usernameField));
        content.add(Box.createVerticalStrut(6));
        content.add(labelled("Contrasena", passwordField));
        content.add(Box.createVerticalStrut(10));

        messageLabel.setForeground(new java.awt.Color(180, 0, 0));
        messageLabel.setAlignmentX(CENTER_ALIGNMENT);
        content.add(messageLabel);
        content.add(Box.createVerticalStrut(6));

        loginButton.setAlignmentX(CENTER_ALIGNMENT);
        loginButton.addActionListener(event -> attemptLogin());
        content.add(loginButton);

        passwordField.addActionListener(event -> attemptLogin());
        getRootPane().setDefaultButton(loginButton);
        setContentPane(content);
    }

    private JPanel labelled(String label, java.awt.Component field) {
        JPanel panel = new JPanel(new java.awt.BorderLayout(8, 0));
        JLabel text = new JLabel(label, SwingConstants.RIGHT);
        text.setPreferredSize(new java.awt.Dimension(90, 24));
        panel.add(text, java.awt.BorderLayout.WEST);
        panel.add(field, java.awt.BorderLayout.CENTER);
        return panel;
    }

    private void attemptLogin() {
        String username = usernameField.getText();
        String password = new String(passwordField.getPassword());
        loginButton.setEnabled(false);
        messageLabel.setText("Verificando...");
        Async.run(
                () -> authService.login(username, password),
                result -> {
                    loginButton.setEnabled(true);
                    if (result.isOk()) {
                        user = result.value();
                        dispose();
                    } else {
                        messageLabel.setText(result.problems().get(0));
                    }
                },
                failure -> {
                    loginButton.setEnabled(true);
                    messageLabel.setText("Error de conexion: " + failure.getMessage());
                });
    }
}
