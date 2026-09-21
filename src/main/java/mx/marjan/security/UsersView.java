package mx.marjan.security;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import mx.marjan.shared.Async;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Result;
import mx.marjan.shared.Ui;

public class UsersView extends BaseView {

    private final AuthService authService = new AuthService();
    private final RecordTableModel<User> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Usuario", User::username),
            RecordTableModel.Column.of("Rol", User::roleName),
            RecordTableModel.Column.of("Estado", user -> user.status().dbValue())));
    private final JTable table = Ui.table(model);
    private List<Role> roles = List.of();

    public UsersView() {
        add(Ui.row(Ui.button("Nuevo", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Restablecer contrasena", this::resetPassword),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        Async.run(authService::roles, loaded -> roles = loaded, failure -> Ui.failure(this, failure));
        reload();
    }

    @Override
    public void reload() {
        load(authService::listUsers, model::setRows);
    }

    private User selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void openNew() {
        if (roles.isEmpty()) {
            Ui.info(this, "Cargando roles, intente de nuevo");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("username", "Usuario", "")
                .addPassword("password", "Contrasena")
                .addCombo("role", "Rol", roles.toArray(), roles.get(0))
                .addCombo("status", "Estado", UserStatus.values(), UserStatus.ACTIVE);
        ModalForm.show(this, "Nuevo usuario", form, () -> {
            Role role = (Role) form.selected("role");
            return authService.createUser(form.text("username"), form.text("password"),
                    role.id(), null, (UserStatus) form.selected("status"));
        }, this::reload);
    }

    private void openEdit() {
        User user = selected();
        if (user == null) {
            Ui.info(this, "Seleccione un usuario");
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
        User user = selected();
        if (user == null) {
            Ui.info(this, "Seleccione un usuario");
            return;
        }
        JTextField field = new JTextField(16);
        Object[] message = { "Nueva contrasena para " + user.username() + ":", field };
        if (javax.swing.JOptionPane.showConfirmDialog(this, message, "Restablecer contrasena",
                javax.swing.JOptionPane.OK_CANCEL_OPTION) != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        Async.run(() -> authService.resetPassword(user.id(), field.getText()),
                result -> {
                    if (result.isErr()) {
                        Ui.error(this, "Error", result.problems());
                    } else {
                        Ui.info(this, "Contrasena actualizada");
                    }
                },
                failure -> Ui.failure(this, failure));
    }

    private Role roleById(long id) {
        return roles.stream().filter(role -> role.id() == id).findFirst().orElse(null);
    }
}
