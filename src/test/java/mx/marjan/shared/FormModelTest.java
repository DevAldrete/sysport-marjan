package mx.marjan.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormModelTest {

    @Test
    void readsBackTextAreaValue() {
        FormModel form = new FormModel().addArea("description", "Descripcion", "valor inicial");

        assertEquals("valor inicial", form.text("description"));

        form.setText("description", "descripcion nueva");
        assertEquals("descripcion nueva", form.text("description"));
    }

    @Test
    void readsPlainFields() {
        FormModel form = new FormModel()
                .addText("name", "Nombre", "Ana")
                .addCombo("role", "Rol", new Object[] { "a", "b" }, "b")
                .addCheck("enabled", "Activo", true);

        assertEquals("Ana", form.text("name"));
        assertEquals("b", form.selected("role"));
        assertTrue(form.checked("enabled"));
    }

    @Test
    void computedFieldFollowsItsInputs() {
        FormModel form = new FormModel();
        form.addText("liters", "Litros", "2");
        form.addText("price", "Precio", "3");
        form.addComputed("amount", "Importe", () -> {
            BigDecimal liters = Numbers.parseOrZero(form.text("liters"));
            BigDecimal price = Numbers.parseOrZero(form.text("price"));
            return liters.multiply(price).toPlainString();
        });
        form.refresh();
        assertEquals("6", form.text("amount"));

        form.setText("liters", "5");
        form.refresh();
        assertEquals("15", form.text("amount"));
    }

    @Test
    void validatorsOnlyReportTouchedFields() {
        FormModel form = new FormModel().addText("date", "Fecha", "31/01/2026");
        form.validate("date", Validators.date());

        assertTrue(form.validate().isEmpty(), "untouched fields do not report");

        form.markTouched("date");
        Map<String, String> messages = form.validate();
        assertNotNull(messages.get("date"));
    }

    @Test
    void validatorsAcceptValidValues() {
        FormModel form = new FormModel().addText("date", "Fecha", "2026-01-31");
        form.validate("date", Validators.date());
        form.markTouched("date");

        assertTrue(form.validate().isEmpty());
        assertFalse(form.isComputed("date"));
    }

    @Test
    void tracksComputedFields() {
        FormModel form = new FormModel().addText("a", "A", "1");
        form.addComputed("b", "B", () -> form.text("a"));

        assertTrue(form.isComputed("b"));
        assertFalse(form.isComputed("a"));
        assertEquals(2, form.fields().size());
        assertNull(form.baseHint("b"));
    }
}
