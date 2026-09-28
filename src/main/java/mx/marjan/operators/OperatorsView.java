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
            RecordTableModel.Column.of("Vence", employee -> licenseLabel(employee.license(), today)),
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
        loadRows(() -> service.search(term), model::setRows);
    }

    private static String licenseLabel(License license, LocalDate today) {
        if (license == null || license.expirationDate() == null) {
            return "Sin licencia";
        }
        if (license.expirationDate().isBefore(today)) {
            return "VENCIDA (" + license.expirationDate() + ")";
        }
        if (!license.expirationDate().isAfter(today.plusDays(30))) {
            return "Por vencer (" + license.expirationDate() + ")";
        }
        return license.expirationDate().toString();
    }

    private Employee selected() {
        return selectedRow(table, model);
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
        String numberHint = license.licenseNumber() == null || license.licenseNumber().isBlank()
                ? "Automatico al guardar" : license.licenseNumber();
        LicenseType initialType = LicenseType.fromDb(license.licenseType()) != null
                ? LicenseType.fromDb(license.licenseType()) : LicenseType.FEDERAL_C;
        FormPanel form = new FormPanel()
                .addText("name", "Nombre", employee.name())
                .addText("phone", "Telefono", employee.phone())
                .addText("email", "Correo", employee.email())
                .addText("address", "Domicilio", employee.address())
                .addText("rfc", "RFC", employee.rfc())
                .addText("curp", "CURP", employee.curp())
                .addText("ecName", "Contacto de emergencia", employee.emergencyContactName())
                .addText("ecPhone", "Telefono de emergencia", employee.emergencyContactPhone())
                .addText("licenseNumber", "No. de licencia", numberHint,
                        "El sistema asigna el numero interno automaticamente")
                .addCombo("licenseType", "Tipo de licencia", LicenseType.values(), initialType)
                .addDate("licenseIssue", "Expedicion", license.issueDate())
                .addDate("licenseExpiry", "Vencimiento", license.expirationDate());
        form.field("licenseNumber").setEnabled(false);
        ModalForm.show(this, isNew ? "Nuevo operador" : "Editar operador", form, () -> {
            License builtLicense = null;
            Object typeValue = form.selected("licenseType");
            if (typeValue instanceof LicenseType type) {
                LocalDate issue = form.date("licenseIssue");
                LocalDate expiry = form.date("licenseExpiry");
                builtLicense = new License(license.id(), license.licenseNumber(),
                        type.dbValue(), issue, expiry);
            }
            Employee built = new Employee(employee.id(), form.text("name"), form.text("address"),
                    form.text("phone"), form.text("email"), form.text("rfc"), form.text("curp"),
                    form.text("ecName"), form.text("ecPhone"), builtLicense,
                    employee.status());
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
        EmployeeStatus[] options = EmployeeStatus.manualValues();
        EmployeeStatus initial = employee.status().isManual() ? employee.status() : options[0];
        FormPanel form = new FormPanel().addCombo("status", "Nuevo estado", options, initial);
        ModalForm.show(this, "Cambiar estado de " + employee.name(), form,
                () -> service.setStatus(employee.id(), (EmployeeStatus) form.selected("status")),
                this::reload);
    }
}
