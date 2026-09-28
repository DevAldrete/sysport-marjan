---
title: Web UI toolkit
description: The reusable Vue components every screen is built from.
---

Every screen is assembled from the same small set of components, mirroring the roles the old
Swing toolkit played.

## `FormDialog`

The workhorse. Fields are declared as data; a zod schema validates them; the server's `problems`
are shown as a list and the dialog stays open until the write succeeds.

```ts
const fields: FormField[] = [
  { key: 'name', label: 'Nombre o razon social', full: true },
  { key: 'clientType', label: 'Tipo', type: 'select', options: clientTypes },
  { key: 'creditLimit', label: 'Limite de credito', type: 'money' },
]
const schema = z.object({ name: z.string().min(1, 'El nombre es obligatorio') /* … */ })
```

```vue
<FormDialog
  v-model:open="formOpen"
  title="Nuevo cliente"
  :fields="fields"
  :initial="initial"
  :schema="schema"
  :problems="problems"
  :submitting="submitting"
  @submit="save"
/>
```

Field types: `text`, `password`, `number`, `money`, `date`, `datetime`, `textarea`, `select`,
`checkbox`. The dialog also emits `change` (useful for live hints, e.g. the capacity check in the
assignment dialog) and accepts a default slot for custom content (the package editor).

## `ConfirmDialog`

A small wrapper over the alert dialog for destructive or irreversible actions (delete, cancel,
close). Used with a `confirm` callback.

## `PageHeader`

Title, optional description and an actions slot, so every screen's header looks the same.

## Tables

Screens use the shadcn-vue `Table` primitives directly (header, body, rows, cells). Row actions
live in a `DropdownMenu` so the table stays readable; double-click on a row opens its detail.

## Domain dialogs

Reusable dialogs that compose the primitives:

- `PackageEditor` — in-form line editor with a live total weight.
- `AssignmentDialog` / `TripReassignDialog` — eligible vehicle/operator pickers with the BR-08
  capacity hint.
- `RequestDetailDialog` — the request "whole story" (packages + trip).
- `TripDetailDialog` — the 7-tab trip workspace (resumen, gastos, combustible, anticipos,
  incidencias, paquetes, entrega).

## Formatting and vocabulary

- `lib/enums.ts` holds every enum's `dbValue`→Spanish label catalog.
- `lib/format.ts` formats money (`es-MX`) and dates (`AAAA-MM-DD` → `DD/MM/AAAA`).
- `lib/utils.ts` exposes `cn()` for merging Tailwind classes.

## Permission-aware actions

Components read the auth store and hide or disable actions the user cannot perform
(`auth.can('clients.write')`). The API enforces the same permissions, so this is UX only.

Next: [The API](/architecture/api/).
