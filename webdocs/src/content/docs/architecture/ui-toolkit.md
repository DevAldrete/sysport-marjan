---
title: UI toolkit
description: The shared Swing building blocks every screen is assembled from.
---

Every screen is built from a small set of classes in `mx.marjan.shared`. Learn these and you can
read any view.

## The golden rule: never block the EDT

All database work runs off the Event Dispatch Thread through `Async.run`:

```java
Async.run(
    () -> service.search(filter),        // background: does the DB work
    rows -> model.setRows(rows),         // EDT: update the UI
    failure -> Ui.failure(this, failure) // EDT: report errors
);
```

`Async` is a thin `SwingWorker` wrapper that unwraps the cause so you get the real exception and
shows a **wait cursor** on every window while at least one worker runs. `BaseView` wraps this
further.

## `BaseView` — the screen shell

Every list screen extends `BaseView`. It provides a padded `BorderLayout`, a status label at the
bottom, and data-loading helpers:

```java
public abstract class BaseView extends JPanel {
    public abstract void reload();

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess);
    protected <T> void loadRows(Callable<List<T>> task, Consumer<List<T>> onSuccess);
    protected void setStatus(String message);
    protected <T> T selectedRow(JTable table, RecordTableModel<T> model);
}
```

- `load` shows **"Cargando..."** while the task runs.
- `loadRows` additionally shows **"Sin resultados"** when the list is empty.
- `selectedRow` returns the selected record (respecting the table sorter) or `null`.

## `FormPanel` — labelled forms with live feedback

`FormPanel` builds a labelled form and lets you read values back by key. It also gives forms
**hints**, **live validation** and **computed fields** for free.

```java
FormPanel form = new FormPanel()
        .addCombo("client", "Cliente", clients.toArray(), clients.get(0))
        .addText("weight", "Peso aproximado (kg)", "0", "En kilogramos, ej. 1200")
        .addText("pickup", "Recoleccion (opcional)", "", "Formato: AAAA-MM-DD HH:MM")
        .addCheck("documents", "Requiere documentacion", true)
        .addArea("notes", "Observaciones", "", "Notas internas (opcional)");
form.validate("weight", Validators.number());
form.validate("pickup", Validators.dateTime());
```

| Method | Purpose |
| --- | --- |
| `addText(key, label, value[, hint])` | text field |
| `addPassword(key, label[, hint])` | masked field |
| `addCombo(key, label, items, selected)` | dropdown |
| `addCheck(key, label, value)` | checkbox |
| `addArea(key, label, value[, hint])` | multi-line text area |
| `addComputed(key, label, supplier)` | **read-only**, recomputed on every change |
| `addSection(component)` | full-width custom row (e.g. a child-list editor) |
| `validate(key, fn)` | live check; returns a message or `null` |
| `hint(key, text)` | persistent helper text (also used as the field tooltip) |
| `onSelect(key, action)` | run when a combo changes (e.g. prefill) |
| `onChange(listener)` | run after any change |
| `isValid()`, `focusFirstInvalid()` | mark all fields touched; block submit / focus the first red one |
| `text(key)`, `selected(key)`, `checked(key)`, `setText(key, v)`, `field(key)` | read/write |

A field with an invalid value is outlined red and its hint turns into the error message **as the
user types** (once they have edited it). Computed fields are perfect for derived values:

```java
// FuelLoadsView: Importe = litros × precio, always in sync
form.addComputed("amount", "Importe", () -> Money.format(amountFor(form)));
```

## `ModalForm` — standard dialogs

`ModalForm.show(...)` opens a modal dialog with **Guardar / Cancelar**, runs the submit handler off
the EDT, keeps the dialog open and shows the problems on error, and closes on success:

```java
ModalForm.show(this, "Nueva ruta", form, () -> service.save(built), this::reload);
```

- The submit handler returns `Result<?>`.
- **Submit is gated on live validation**: if a validated field is still red, the dialog focuses it
  and does not call the handler.
- On `Result.Err`, the problems are shown under *"No se pudo guardar"* and the user can fix them.
  (The database reports every rule at once; `Database.asProblemList` splits the `'; '` string so
  `Ui.error` shows one bullet per problem.)
- On success the optional `afterSave` runs (usually `reload`).
- The first editable field gets focus automatically.

## `Ui` — small consistent helpers

| Helper | Purpose |
| --- | --- |
| `Ui.error(parent, title, problems)` | error dialog (bulleted when several) |
| `Ui.info(parent, message)` | info dialog |
| `Ui.confirm(parent, message)` | yes/no confirmation |
| `Ui.delete(parent, what, action, onDone)` | confirm + async delete + uniform reporting |
| `Ui.failure(parent, throwable)` | report an unexpected exception |
| `Ui.button(text[, tooltip], action[, enabled])` | a button wired to a `Runnable`; disabled when the user lacks the permission |
| `Ui.onEnter(field, action)` | Enter in a search field |
| `Ui.onDoubleClick(table, action)` | open a row on double-click |
| `Ui.row(…)`, `Ui.column(…)`, `Ui.titled(…)` | layout helpers |
| `Ui.table(model)`, `Ui.style(table)` | apply the shared table look (sorter, row height) |

## Tables — one generic model

There is **one** table model, `RecordTableModel<T>`. Columns are lambdas:

```java
private final RecordTableModel<Route> model = new RecordTableModel<>(List.of(
        RecordTableModel.Column.of("Origen", Route::origin),
        RecordTableModel.Column.of("Destino", Route::destination),
        RecordTableModel.Column.text("Descripcion", Route::description, 60)));

private final JTable table = Ui.table(model);
```

- `Column.of(title, getter)` — value straight from the record.
- `Column.text(title, getter, max)` — collapsed to one line and truncated with an ellipsis.
- `model.setRows(list)` refreshes the table.

For a table + selection + action row (used in detail dialogs), `RecordTablePanel<T>` bundles them:

```java
RecordTablePanel<Payment> panel = new RecordTablePanel<>(paymentsModel);
panel.withActions(Ui.button("Eliminar pago", …), Ui.button("Cerrar", dialog::dispose));
panel.setRows(service.paymentsFor(invoice.id()));
panel.selected();   // current row or null
```

## Small value helpers

| Class | Purpose |
| --- | --- |
| `Money` | `format`, `parse`, `require(text, field)`; always `BigDecimal` |
| `Numbers` | `parseOrZero`, `plain` for decimal fields |
| `Dates` | `format`, `parseDate`, `parseDateTime` (lenient), `today`, `now` |
| `Text` | `truncate` / `label` for compact table cells |
| `Validators` | `isValidUsername` + live `date`/`dateTime`/`money`/`number` checks |

## Screen anatomy

```text
┌───────────────────────────────────────────────┐
│ filters row            (search, combos)        │  NORTH
│ actions row            (Nuevo, Editar, …)      │
├───────────────────────────────────────────────┤
│ JTable (RecordTableModel, sortable)            │  CENTER
│                                               │
├───────────────────────────────────────────────┤
│ status label ("Cargando…" / "Sin resultados")  │  SOUTH (BaseView)
└───────────────────────────────────────────────┘
```

Next: [Conventions](/architecture/conventions/).
