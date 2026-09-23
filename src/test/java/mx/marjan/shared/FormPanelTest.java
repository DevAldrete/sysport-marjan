package mx.marjan.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
