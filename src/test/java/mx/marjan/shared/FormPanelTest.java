package mx.marjan.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FormPanelTest {

    @Test
    void readsBackTextAreaValue() {
        FormPanel form = new FormPanel().addArea("description", "Descripcion", "valor inicial");

        assertEquals("valor inicial", form.text("description"));

        form.setText("description", "descripcion nueva");
        assertEquals("descripcion nueva", form.text("description"));
    }

    @Test
    void readsPlainFields() {
        FormPanel form = new FormPanel()
                .addText("name", "Nombre", "Ana")
                .addCombo("role", "Rol", new Object[] { "a", "b" }, "b")
                .addCheck("enabled", "Activo", true);

        assertEquals("Ana", form.text("name"));
        assertEquals("b", form.selected("role"));
        assertTrue(form.checked("enabled"));
    }

    @Test
    void readsBackDateAndDateTimeFields() {
        FormPanel form = new FormPanel()
                .addDate("date", "Fecha", LocalDate.of(2026, 1, 31))
                .addDate("optional", "Opcional", null)
                .addDateTime("when", "Fecha y hora", LocalDateTime.of(2026, 1, 31, 8, 30));

        assertEquals(LocalDate.of(2026, 1, 31), form.date("date"));
        assertNull(form.date("optional"));
        assertEquals(LocalDateTime.of(2026, 1, 31, 8, 30), form.dateTime("when"));
    }
}
