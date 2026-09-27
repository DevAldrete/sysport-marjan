package mx.marjan.operators;

import java.time.LocalDate;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import mx.marjan.shared.Dates;
import mx.marjan.shared.Result;
import mx.marjan.ui.BaseView;
import mx.marjan.ui.FormPanel;
import mx.marjan.ui.Icons;
import mx.marjan.ui.ModalForm;
import mx.marjan.ui.RecordTable;
import mx.marjan.ui.StatusBadge;
import mx.marjan.ui.Ui;
import org.kordamp.ikonli.feather.Feather;

public class OperatorsView extends BaseView {

    private final EmployeeService service = new EmployeeService();
    private final LocalDate today = Dates.today();
    private final RecordTable<Employee> table = new RecordTable<>(List.of(
            RecordTable.Column.of("Nombre", Employee::name),
            RecordTable.Column.of("Telefono", Employee::phone),
            RecordTable.Column.of("Licencia", employee -> employee.license() == null
                    ? "Sin licencia" : employee.license().licenseNumber()),
            RecordTable.Column.badge("Vence", employee -> licenseLabel(employee.license()),
                    this::licenseTone),
            RecordTable.Column.badge("Estado", employee -> employee.status().label(),
                    employee -> mx.marjan.ui.StatusTones.employee(employee.status()))));
    private final TextField search = new TextField();

    public OperatorsView() {
        search.setPromptText("Nombre");
        search.setOnAction(event -> reload());
        Ui.onDoubleClick(table, this::openForm);

        var nuevo = Ui.primary("Nuevo", this::openNew);
        nuevo.setGraphic(Icons.action(Feather.PLUS));
        var filters = Ui.filters(new Label("Buscar:"), search, Ui.button("Buscar", this::reload));
        var actions = Ui.toolbar(nuevo,
                Ui.button("Editar", this::openEdit),
                Ui.button("Cambiar estado", this::changeStatus),
                Ui.button("Eliminar", this::deleteOperator),
                Ui.button("Recargar", this::reload));
        setTop(new VBox(4, filters, actions));
        setCenter(table);
        reload();
    }

    @Override
    public void reload() {
        loadRows(() -> service.search(search.getText()), table::setRows);
    }

    private String licenseLabel(License license) {
        if (license == null || license.expirationDate() == null) {
            return "Sin licencia";
        }
        if (license.expirationDate().isBefore(today)) {
            return "Vencida " + license.expirationDate();
        }
        if (!license.expirationDate().isAfter(today.plusDays(30))) {
            return "Por vencer " + license.expirationDate();
        }
        return license.expirationDate().toString();
    }

    private StatusBadge.Tone licenseTone(Employee employee) {
        License license = employee.license();
        if (license == null || license.expirationDate() == null) {
            return StatusBadge.Tone.NEUTRAL;
        }
        if (license.expirationDate().isBefore(today)) {
            return StatusBadge.Tone.DANGER;
        }
        if (!license.expirationDate().isAfter(today.plusDays(30))) {
            return StatusBadge.Tone.WARNING;
        }
        return StatusBadge.Tone.SUCCESS;
    }

    private void openNew() {
        openForm(Employee.empty());
    }

    private void openEdit() {
        Employee employee = table.selected();
        if (employee == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un operador");
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
                .addText("licenseExpiry", "Vencimiento (yyyy-MM-dd)", Dates.format(license.expirationDate()));
        form.validate("licenseIssue", mx.marjan.shared.Validators.date());
        form.validate("licenseExpiry", mx.marjan.shared.Validators.date());
        ModalForm.show(Ui.windowOf(this), isNew ? "Nuevo operador" : "Editar operador", form, () -> {
            License builtLicense = null;
            String number = form.text("licenseNumber");
            if (!number.isBlank()) {
                LocalDate issue = form.text("licenseIssue").isBlank() ? null
                        : Dates.parseDate(form.text("licenseIssue")).orElse(null);
                LocalDate expiry = Dates.parseDate(form.text("licenseExpiry")).orElse(null);
                builtLicense = new License(license.id(), number, form.text("licenseType"), issue, expiry);
            }
            Employee built = new Employee(employee.id(), form.text("name"), form.text("address"),
                    form.text("phone"), form.text("email"), form.text("rfc"), form.text("curp"),
                    form.text("ecName"), form.text("ecPhone"), builtLicense, employee.status());
            return service.save(built);
        }, this::reload);
    }

    private void changeStatus() {
        Employee employee = table.selected();
        if (employee == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un operador");
            return;
        }
        EmployeeStatus[] options = EmployeeStatus.manualValues();
        EmployeeStatus initial = employee.status().isManual() ? employee.status() : options[0];
        FormPanel form = new FormPanel().addCombo("status", "Nuevo estado", options, initial);
        ModalForm.show(Ui.windowOf(this), "Cambiar estado de " + employee.name(), form,
                () -> service.setStatus(employee.id(), (EmployeeStatus) form.selected("status")),
                this::reload);
    }

    private void deleteOperator() {
        Employee employee = table.selected();
        if (employee == null) {
            Ui.info(Ui.windowOf(this), "Seleccione un operador");
            return;
        }
        Ui.delete(Ui.windowOf(this), "el operador \"" + employee.name() + "\"",
                () -> service.delete(employee.id()), this::reload);
    }
}
