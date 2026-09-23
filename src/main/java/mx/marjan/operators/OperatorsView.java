package mx.marjan.operators;

import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import mx.marjan.shared.BaseView;
import mx.marjan.shared.Dates;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.ModalForm;
import mx.marjan.shared.RecordTableModel;
import mx.marjan.shared.Ui;

public class OperatorsView extends BaseView {

    private final EmployeeService service = new EmployeeService();
    private final LocalDate today = Dates.today();
    private final RecordTableModel<Employee> model = new RecordTableModel<>(List.of(
            RecordTableModel.Column.of("Nombre", Employee::name),
            RecordTableModel.Column.of("Telefono", Employee::phone),
            RecordTableModel.Column.of("Licencia", employee -> employee.license() == null
                    ? "Sin licencia" : employee.license().licenseNumber()),
            RecordTableModel.Column.of("Vence", employee -> LicenseRules.expiryLabel(employee.license(), today)),
            RecordTableModel.Column.of("Estado", employee -> employee.status().label())));
    private final JTable table = Ui.table(model);
    private final JTextField searchField = new JTextField(18);

    public OperatorsView() {
        add(Ui.row(new JLabel("Buscar:"), searchField,
                Ui.button("Buscar", this::reload),
                Ui.button("Nuevo", this::openNew),
                Ui.button("Editar", this::openEdit),
                Ui.button("Cambiar estado", this::changeStatus),
                Ui.button("Eliminar", this::deleteOperator),
                Ui.button("Recargar", this::reload)), BorderLayout.NORTH);
        add(Ui.scroll(table), BorderLayout.CENTER);
        reload();
    }

    @Override
    public void reload() {
        String term = searchField.getText();
        load(() -> service.search(term), model::setRows);
    }

    private Employee selected() {
        int row = table.getSelectedRow();
        return row < 0 ? null : model.rowAt(table.convertRowIndexToModel(row));
    }

    private void openNew() {
        openForm(Employee.empty());
    }

    private void openEdit() {
        Employee employee = selected();
        if (employee == null) {
            Ui.info(this, "Seleccione un operador");
            return;
        }
        openForm(employee);
    }

    private void openForm(Employee employee) {
        boolean isNew = employee.id() == 0;
        License license = employee.license() != null ? employee.license() : License.empty();
        FormPanel form = new FormPanel()
                .addText("name", "Nombre", employee.name())
                .addText("phone", "Telefono", employee.phone())
                .addText("email", "Correo", employee.email())
                .addText("address", "Domicilio", employee.address())
                .addText("rfc", "RFC", employee.rfc())
                .addText("curp", "CURP", employee.curp())
                .addText("ecName", "Contacto de emergencia", employee.emergencyContactName())
                .addText("ecPhone", "Telefono de emergencia", employee.emergencyContactPhone())
                .addText("licenseNumber", "No. de licencia", license.licenseNumber())
                .addText("licenseType", "Tipo de licencia", license.licenseType())
                .addText("licenseIssue", "Expedicion (yyyy-MM-dd)", Dates.format(license.issueDate()))
                .addText("licenseExpiry", "Vencimiento (yyyy-MM-dd)", Dates.format(license.expirationDate()))
                .addCombo("status", "Estado", EmployeeStatus.values(), employee.status());
        ModalForm.show(this, isNew ? "Nuevo operador" : "Editar operador", form, () -> {
            License builtLicense = null;
            String number = form.text("licenseNumber");
            if (!number.isBlank()) {
                LocalDate issue = form.text("licenseIssue").isBlank() ? null
                        : Dates.parseDate(form.text("licenseIssue")).orElse(null);
                LocalDate expiry = Dates.parseDate(form.text("licenseExpiry")).orElse(null);
                builtLicense = new License(license.id(), number, form.text("licenseType"),
                        issue, expiry);
            }
            Employee built = new Employee(employee.id(), form.text("name"), form.text("address"),
                    form.text("phone"), form.text("email"), form.text("rfc"), form.text("curp"),
                    form.text("ecName"), form.text("ecPhone"), builtLicense,
                    (EmployeeStatus) form.selected("status"));
            return service.save(built);
        }, this::reload);
    }

    private void deleteOperator() {
        Employee employee = selected();
        if (employee == null) {
            Ui.info(this, "Seleccione un operador");
            return;
        }
        Ui.delete(this, "el operador \"" + employee.name() + "\"",
                () -> service.delete(employee.id()), this::reload);
    }

    private void changeStatus() {
        Employee employee = selected();
        if (employee == null) {
            Ui.info(this, "Seleccione un operador");
            return;
        }
        FormPanel form = new FormPanel().addCombo("status", "Nuevo estado",
                EmployeeStatus.values(), employee.status());
        ModalForm.show(this, "Cambiar estado de " + employee.name(), form, () -> {
            EmployeeStatus status = (EmployeeStatus) form.selected("status");
            var result = service.setStatus(employee.id(), status);
            return result;
        }, this::reload);
    }
}
