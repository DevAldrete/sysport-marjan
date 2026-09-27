package mx.marjan.security;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JTable;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Ui;
import mx.marjan.shared.Validators;

public class UsersView extends BaseView {

    private final AuthService authService = new AuthService();
    private final RecordTableModel<User> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Usuario", User::username),
            RecordTableModel.Column.of("Rol", User::roleName),
            RecordTableModel.Column.of("Estado", user -> user.status().dbValue())));
    private final JTable table = Ui.table(model);
    private List<Role> roles = List.of();

    public UsersView() {
        Ui.onDoubleClick(table, this::openEdit);
        add(Ui.row(Ui.button("Nuevo", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Restablecer contrasena", this::resetPassword),
                Ui.button("Eliminar", this::deleteUser),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        Async.run(authService::roles, loaded -> roles = loaded, failure -> Ui.failure(this, failure));
        reload();
    }

    @Override
    public void reload() {
        loadRows(authService::listUsers, model::setRows);
    }

    private User selected() {
        return selectedRow(table, model);
    }

    private User requireSelected() {
        User user = selected();
        if (user == null) {
            Ui.info(this, "Seleccione un usuario");
        }
        return user;
    }

    private void openNew() {
        if (roles.isEmpty()) {
            Ui.info(this, "Cargando roles, intente de nuevo");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("username", "Usuario", "", "3-50 caracteres: letras, numeros, . _ -")
                .addPassword("password", "Contrasena", "Minimo 6 caracteres")
                .addCombo("role", "Rol", roles.toArray(), roles.get(0))
                .addCombo("status", "Estado", UserStatus.values(), UserStatus.ACTIVE);
        form.validate("username", username -> username == null || username.isBlank()
                || Validators.isValidUsername(username) ? null
                        : "3-50 caracteres: letras, numeros, . _ -");
        form.validate("password", password -> password == null || password.length() >= 6 ? null
                : "Minimo 6 caracteres");
        ModalForm.show(this, "Nuevo usuario", form, () -> {
            Role role = (Role) form.selected("role");
            return authService.createUser(form.text("username"), form.text("password"),
                    role.id(), null, (UserStatus) form.selected("status"));
        }, this::reload);
    }

    private void openEdit() {
        User user = requireSelected();
        if (user == null) {
            return;
        }
        if (roles.isEmpty()) {
            Ui.info(this, "Cargando roles, intente de nuevo");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("username", "Usuario", user.username())
                .addCombo("role", "Rol", roles.toArray(), roleById(user.roleId()))
                .addCombo("status", "Estado", UserStatus.values(), user.status());
        ModalForm.show(this, "Editar usuario", form, () -> {
            Role role = (Role) form.selected("role");
            return authService.updateUser(user.id(), form.text("username"), role.id(),
                    user.employeeId(), (UserStatus) form.selected("status"));
        }, this::reload);
    }

    private void resetPassword() {
        User user = requireSelected();
        if (user == null) {
            return;
        }
        FormPanel form = new FormPanel().addPassword("password", "Nueva contrasena", "Minimo 6 caracteres");
        form.validate("password", password -> password == null || password.length() >= 6 ? null
                : "Minimo 6 caracteres");
        ModalForm.show(this, "Restablecer contrasena de " + user.username(), form,
                () -> authService.resetPassword(user.id(), form.text("password")),
                () -> Ui.info(this, "Contrasena actualizada"));
    }

    private void deleteUser() {
        User user = requireSelected();
        if (user == null) {
            return;
        }
        Ui.delete(this, "el usuario \"" + user.username() + "\"",
                () -> authService.deleteUser(user.id()), this::reload);
    }

    private Role roleById(long id) {
        return roles.stream().filter(role -> role.id() == id).findFirst().orElse(null);
    }
}
