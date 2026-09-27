---
title: UI toolkit
description: The shared JavaFX building blocks every screen is assembled from.
---

Every screen is built from a small set of classes in `mx.marjan.ui`. Learn these and you can read
any view. The theme is [AtlantaFX](https://github.com/mkpaz/atlantafx) (Primer light/dark) with a
black-and-white brand accent; icons come from the Ikonli Feather pack.

## The golden rule: never block the FX thread

All database work runs off the JavaFX Application Thread through `Async.run`:

```java
Async.run(
    () -> service.search(filter),        // background: does the DB work
    rows -> model.setRows(rows),         // FX thread: update the UI
    failure -> Ui.failure(window, failure) // FX thread: report errors
);
```

`Async` is a thin `javafx.concurrent.Task` wrapper that marshals the callbacks back to the FX
thread. `BaseView` wraps this further.

## `AppShell` and `Navigation` — the window

`AppShell` is the main window: a permission-aware sidebar (`Navigation`), an app bar with the
light/dark toggle and the user menu, and a content area. `Navigation` is built from `Item`s
(group, title, icon, permission, screen factory) and only shows the items the current user may
see. Dashboard cards call back into the shell to navigate.

## `BaseView` — the screen shell

Every screen extends `BaseView`. It provides padding, a status bar with a spinner, and data-loading
helpers:

```java
public abstract class BaseView extends BorderPane {
    public abstract void reload();

    protected <T> void load(Callable<T> task, Consumer<T> onSuccess);
    protected <T> void loadRows(Callable<List<T>> task, Consumer<List<T>> onSuccess);
    protected void setStatus(String message);
}
```

- `load` shows **"Cargando..."** while the task runs.
- `loadRows` additionally shows **"Sin resultados"** when the list is empty.

## `FormModel` + `FormPanel` — labelled forms with live feedback

Form **state** lives in a pure, UI-free `shared/FormModel`; `ui/FormPanel` renders it into JavaFX
inputs. That split keeps form logic unit-testable without a toolkit.

```java
FormPanel form = new FormPanel()
        .addCombo("client", "Cliente", clients.toArray(), clients.get(0))
        .addText("weight", "Peso aproximado (kg)", "0", "En kilogramos, ej. 1200")
        .addCheck("documents", "Requiere documentacion", true)
        .addArea("notes", "Observaciones", "", "Notas internas (opcional)");
form.validate("weight", Validators.number());
```

| Method | Purpose |
| --- | --- |
| `addText(key, label, value[, hint])` | text field |
| `addPassword(key, label[, hint])` | masked field |
| `addCombo(key, label, items, selected)` | dropdown |
| `addCheck(key, label, value)` | checkbox |
| `addArea(key, label, value[, hint])` | multi-line text area |
| `addComputed(key, label, supplier)` | **read-only**, recomputed on every change |
| `validate(key, fn)` | live check; returns a message or `null` |
| `hint(key, text)` | persistent helper text |
| `onSelect(key, action)` | run when a combo changes (e.g. prefill) |
| `onChange(listener)` | run after any change |
| `text(key)`, `selected(key)`, `checked(key)`, `setText(key, v)`, `control(key)` | read/write |

A field with an invalid value is outlined and its hint turns into the error message **as the user
types** (once they have edited it). Computed fields are perfect for derived values:

```java
// FuelLoadsView: Importe = litros × precio, always in sync
form.addComputed("amount", "Importe", () -> Money.format(amountFor(form)));
```

## `ModalForm` — standard dialogs

`ModalForm.show(...)` opens a modal dialog with **Guardar / Cancelar**, runs the submit handler off
the FX thread, keeps the dialog open and shows the problems on error, and closes on success:

```java
ModalForm.show(Ui.windowOf(this), "Nueva ruta", form, () -> service.save(built), this::reload);
```

- The submit handler returns `Result<?>`.
- On `Result.Err`, the problems are shown under the form and the user can fix them.
- On success the optional `afterSave` runs (usually `reload`).
- The first editable field gets focus automatically.

## `Ui` — small consistent helpers

| Helper | Purpose |
| --- | --- |
| `Ui.error(window, title, problems)` | error dialog (bulleted when several) |
| `Ui.info/success(window, message)` | information dialogs |
| `Ui.confirm/confirmDanger(window, message[, verb])` | yes/no confirmations |
| `Ui.delete(window, what, action, onDone)` | confirm + async delete + uniform reporting |
| `Ui.failure(window, throwable)` | report an unexpected exception |
| `Ui.button/primary(text[, tooltip], action)` | a button wired to a `Runnable` |
| `Ui.onDoubleClick(table, action)` | open a row on double-click |
| `Ui.toolbar(...)`, `Ui.filters(...)` | wrapping action / filter rows |
| `Ui.windowOf(node)` | the owning `Window` for dialogs |

## Tables — one generic table

There is **one** table class, `RecordTable<T>`. Columns are lambdas:

```java
private final RecordTable<Route> table = new RecordTable<>(List.of(
        RecordTable.Column.of("Origen", Route::origin),
        RecordTable.Column.of("Destino", Route::destination),
        RecordTable.Column.number("Km", Route::estimatedKm),
        RecordTable.Column.text("Descripcion", Route::description, 60)));
```

- `Column.of(title, getter)` — value straight from the record.
- `Column.text(title, getter, max)` — collapsed to one line and truncated with an ellipsis.
- `Column.number/money(title, getter)` — right-aligned; money is formatted.
- `Column.badge(title, getter, tone)` — a colored `StatusBadge` (see `StatusTones`).
- `table.setRows(list)` / `table.selected()`.

For a table + selection + action row (used in detail dialogs), `RecordTablePanel<T>` bundles them:

```java
RecordTablePanel<Payment> panel = new RecordTablePanel<>(new RecordTable<>(columns));
panel.withActions(Ui.button("Eliminar pago", …), Ui.button("Recargar", …));
panel.setRows(service.paymentsFor(invoice.id()));
panel.selected();   // current row or null
```

## Status, theme and icons

| Class | Purpose |
| --- | --- |
| `StatusBadge` / `StatusTones` | colored chips; the single place that maps a status enum to a color |
| `ThemeManager` | Primer light/dark, remembered between sessions; app stylesheet `app.css` |
| `Icons` | `Icons.icon(Feather.TRUCK, 16)` / `Icons.action(...)` |

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
│ filters row            (search, combos)        │  top (VBox)
│ actions row            (Nuevo, Editar, …)      │
├───────────────────────────────────────────────┤
│ RecordTable<T> (sortable, virtualized)         │  center
│                                               │
├───────────────────────────────────────────────┤
│ status bar ("Cargando…" / "Sin resultados")    │  bottom (BaseView)
└───────────────────────────────────────────────┘
```

Next: [Conventions](/architecture/conventions/).
