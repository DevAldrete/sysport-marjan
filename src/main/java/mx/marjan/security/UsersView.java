package mx.marjan.security;

import java.util.List;
import javafx.scene.layout.VBox;
import mx.marjan.ui.Async;
import mx.marjan.shared.Validators;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.Icons;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.StatusBadge;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class UsersView extends BaseView {

    private final AuthService authService = new AuthService();
    private final RecordTable<User> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Usuario", User::username),
            RecordTable.Column.of("Rol", User::roleName),
            RecordTable.Column.badge("Estado", user -> user.status().dbValue(),
                    user -> user.status() == UserStatus.ACTIVE
                            ? StatusBadge.Tone.SUCCESS : StatusBadge.Tone.NEUTRAL)));
    private List<Role> roles = List.of();

    public UsersView() {
        Ui.onDoubleClick(table, user -> openEdit());

        var nuevo = Ui.primary("Nuevo", this::openNew);
        nuevo.setGraphic(Icons.action(Feather.PLUS));
        setTop(new VBox(Ui.toolbar(nuevo,
                Ui.button("Editar", this::openEdit),
                Ui.button("Restablecer contrasena", this::resetPassword),
                Ui.button("Eliminar", this::deleteUser),
                Ui.button("Recargar", this::reload))));
        setCenter(table);

        Async.run(authService::roles, loaded -> roles = loaded,
                failure -> Ui.failure(Ui.windowOf(this), failure));
        reload();
    }

    @Override
    public void reload() {
        loadRows(authService::listUsers, table::setRows);
    }

    private User requireSelected() {
        User user = table.selected();
        if (user == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un usuario");
        }
        return user;
    }

    private void openNew() {
        if (roles.isEmpty()) {
            Ui.info(Ui.windowOf(this), "Cargando roles, intente de nuevo");
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
        ModalForm.show(Ui.windowOf(this), "Nuevo usuario", form, () -> {
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
            Ui.info(Ui.windowOf(this), "Cargando roles, intente de nuevo");
            return;
        }
        FormPanel form = new FormPanel()
                .addText("username", "Usuario", user.username())
                .addCombo("role", "Rol", roles.toArray(), roleById(user.roleId()))
                .addCombo("status", "Estado", UserStatus.values(), user.status());
        ModalForm.show(Ui.windowOf(this), "Editar usuario", form, () -> {
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
        ModalForm.show(Ui.windowOf(this), "Restablecer contrasena de " + user.username(), form,
                () -> authService.resetPassword(user.id(), form.text("password")),
                () -> Ui.success(Ui.windowOf(this), "Contrasena actualizada"));
    }

    private void deleteUser() {
        User user = requireSelected();
        if (user == null) {
            return;
        }
        Ui.delete(Ui.windowOf(this), "el usuario \"" + user.username() + "\"",
                () -> authService.deleteUser(user.id()), this::reload);
    }

    private Role roleById(long id) {
        return roles.stream().filter(role -> role.id() == id).findFirst().orElse(null);
    }
}
