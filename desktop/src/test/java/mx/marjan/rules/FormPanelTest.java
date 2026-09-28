package mx.marjan.rules;

import java.math.BigDecimal;
import mx.marjan.shared.FormPanel;
import mx.marjan.shared.Numbers;
import mx.marjan.shared.Validators;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FormPanelTest {

    @Test
    void computedFieldFollowsItsInputs() {
        FormPanel form = new FormPanel();
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
        assertEquals("15", form.text("amount"));
    }

    @Test
    void liveValidatorsAcceptValidAndRejectInvalidValues() {
        assertNull(Validators.date().apply("2026-01-31"));
        assertNotNull(Validators.date().apply("31/01/2026"));
        assertNull(Validators.dateTime().apply("2026-01-31 08:30"));
        assertNotNull(Validators.dateTime().apply("2026-01-31 25:00"));
        assertNull(Validators.money().apply("$1,500.00"));
        assertNotNull(Validators.money().apply("mil pesos"));
        assertNull(Validators.number().apply("45.5"));
        assertNotNull(Validators.number().apply("cuarenta"));
    }
}
