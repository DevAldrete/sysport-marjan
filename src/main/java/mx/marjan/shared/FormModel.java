package mx.marjan.shared;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The pure state behind a form: field definitions, values, live validators and
 * computed fields. It has no UI dependency, so the logic is unit-testable and
 * the JavaFX {@code FormPanel} only renders it.
 *
 * <p>Validators check format only; business rules stay in the database.</p>
 */
public final class FormModel {

    public enum Kind { TEXT, PASSWORD, COMBO, CHECK, AREA, COMPUTED }

    public record Field(String key, String label, Kind kind, String hint, Object[] items) {}

    private final List<Field> fields = new ArrayList<>();
    private final Map<String, Field> byKey = new LinkedHashMap<>();
    private final Map<String, Object> values = new LinkedHashMap<>();
    private final Map<String, String> baseHints = new LinkedHashMap<>();
    private final Map<String, Function<String, String>> validators = new LinkedHashMap<>();
    private final Map<String, Supplier<String>> computed = new LinkedHashMap<>();
    private final Set<String> touched = new LinkedHashSet<>();

    public FormModel addText(String key, String label, String value) {
        return addText(key, label, value, null);
    }

    public FormModel addText(String key, String label, String value, String hint) {
        put(key, label, Kind.TEXT, hint, null, value == null ? "" : value);
        return this;
    }

    public FormModel addPassword(String key, String label) {
        return addPassword(key, label, null);
    }

    public FormModel addPassword(String key, String label, String hint) {
        put(key, label, Kind.PASSWORD, hint, null, "");
        return this;
    }

    public FormModel addCombo(String key, String label, Object[] items, Object selected) {
        put(key, label, Kind.COMBO, null, items, selected);
        return this;
    }

    public FormModel addCheck(String key, String label, boolean value) {
        put(key, label, Kind.CHECK, null, null, value);
        return this;
    }

    public FormModel addArea(String key, String label, String value) {
        return addArea(key, label, value, null);
    }

    public FormModel addArea(String key, String label, String value, String hint) {
        put(key, label, Kind.AREA, hint, null, value == null ? "" : value);
        return this;
    }

    /** A read-only field whose value is recomputed from the others on every change. */
    public FormModel addComputed(String key, String label, Supplier<String> supplier) {
        put(key, label, Kind.COMPUTED, null, null, "");
        computed.put(key, supplier);
        return this;
    }

    /** A live format check. The message is shown once the user has edited the field. */
    public FormModel validate(String key, Function<String, String> validator) {
        validators.put(key, validator);
        return this;
    }

    /** Persistent helper text shown under a field when it is not reporting an error. */
    public FormModel hint(String key, String text) {
        baseHints.put(key, text);
        Field field = byKey.get(key);
        if (field != null) {
            byKey.put(key, new Field(field.key(), field.label(), field.kind(), text, field.items()));
            int index = fields.indexOf(field);
            if (index >= 0) {
                fields.set(index, byKey.get(key));
            }
        }
        return this;
    }

    private void put(String key, String label, Kind kind, String hint, Object[] items, Object value) {
        Field field = new Field(key, label, kind, hint, items);
        fields.add(field);
        byKey.put(key, field);
        values.put(key, value);
        if (hint != null) {
            baseHints.put(key, hint);
        }
    }

    public List<Field> fields() {
        return List.copyOf(fields);
    }

    public Field field(String key) {
        return byKey.get(key);
    }

    public String label(String key) {
        Field field = byKey.get(key);
        return field == null ? "" : field.label();
    }

    public Kind kind(String key) {
        Field field = byKey.get(key);
        return field == null ? null : field.kind();
    }

    public Object[] items(String key) {
        Field field = byKey.get(key);
        return field == null ? null : field.items();
    }

    public boolean isComputed(String key) {
        return computed.containsKey(key);
    }

    /** Recomputes every computed field from the current values. */
    public void refresh() {
        for (Map.Entry<String, Supplier<String>> entry : computed.entrySet()) {
            values.put(entry.getKey(), entry.getValue().get());
        }
    }

    /** Marks a field as edited by the user so its validator may start reporting. */
    public void markTouched(String key) {
        touched.add(key);
    }

    /** Marks every validated field as edited, so a submit can check the whole form. */
    public void markAllTouched() {
        touched.addAll(validators.keySet());
    }

    /** True when no validated field currently has a problem. */
    public boolean isValid() {
        for (Map.Entry<String, Function<String, String>> entry : validators.entrySet()) {
            if (entry.getValue().apply(text(entry.getKey())) != null) {
                return false;
            }
        }
        return true;
    }

    /** Validation messages for touched fields, keyed by field. Empty when all are valid. */
    public Map<String, String> validate() {
        Map<String, String> messages = new LinkedHashMap<>();
        for (Map.Entry<String, Function<String, String>> entry : validators.entrySet()) {
            String key = entry.getKey();
            if (!touched.contains(key)) {
                continue;
            }
            String message = entry.getValue().apply(text(key));
            if (message != null) {
                messages.put(key, message);
            }
        }
        return messages;
    }

    public String text(String key) {
        Object value = values.get(key);
        return value == null ? "" : value.toString();
    }

    /** Programmatic set; does not count as a user edit. */
    public void setText(String key, String value) {
        values.put(key, value == null ? "" : value);
    }

    public Object selected(String key) {
        return values.get(key);
    }

    /** Programmatic set; does not count as a user edit. */
    public void setSelected(String key, Object value) {
        values.put(key, value);
    }

    public boolean checked(String key) {
        return Boolean.TRUE.equals(values.get(key));
    }

    /** Programmatic set; does not count as a user edit. */
    public void setChecked(String key, boolean value) {
        values.put(key, value);
    }

    public String baseHint(String key) {
        return baseHints.get(key);
    }
}
