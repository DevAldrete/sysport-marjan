---
title: Architecture overview
description: The layered design, the one-way dependency rule, and why the rules live in the database.
---

## The one rule that explains everything

```text
   view (Swing)  →  service  →  repository (JDBC)  →  MySQL
                       ↓
                  records + enums (pure, no I/O)
```

Dependencies point **one way**. A view may call a service; a service may call a repository; a
repository may call the database. Never the reverse. This keeps changes local and testable.

## Layers and responsibilities

| Layer | Responsibility | Must **not** |
| --- | --- | --- |
| **records / enums** | Immutable data (`Client`, `Trip`, …) and typed statuses | Contain logic that needs I/O |
| **routines** (SQL) | Stored procedures & functions: validate, calculate, decide transitions | Live in Java |
| **repository** | Thin JDBC wrappers that call routines; map `ResultSet` ↔ records | Contain business decisions |
| **service** | One use case = one routine call; checks permission; adapts the outcome | Know about Swing |
| **view** | Swing panels: display, collect input, call services off the EDT | Contain SQL or business rules |

## Why the rules live in the database

The database is the **single source of truth**. A rule such as *"a vehicle cannot be double-booked"*
is enforced by `sp_assign_trip` inside a transaction, not by Java. Consequences:

- Changing a rule changes a stored procedure — **no recompile**.
- Concurrency is correct: the rule runs with the row locks it needs.
- There is exactly one place to look and one place to test.
- Java keeps only the **typed vocabulary** (enums) and **transport** (repositories/services).

:::note
The enum's job is only to give the UI a typed, exhaustive set of values. The *transitions* between
them live in `fn_request_can_transition` (see [Lifecycles](/domain/lifecycle/)).
:::

## How OOP and DOP combine

- **Data is dumb and immutable → `record`.** "Changing" means creating a copy.
- **Behaviour is in SQL**, exposed through procedures.
- **Objects are used where they earn their keep:** services and repositories (capabilities) and
  Swing components (inherently object-oriented).
- **Model outcomes explicitly** with enums and sealed types instead of flags and exceptions:

```java
public sealed interface Result<T> permits Result.Ok, Result.Err {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(List<String> problems) implements Result<T> {}
}
```

Rule of thumb: **if you can test it without a database or a window, it belongs in the database as a
function** (or, rarely, in `shared/` as a pure helper).

## Package layout (by feature)

```text
mx.marjan
├── App.java                 entry point
├── MainFrame.java           tabbed main window, permission-guarded tabs
├── shared/                  Database, Result, Async, BaseView, FormPanel, Ui, Theme, Icons, KpiCard, Charts, …
├── security/                users, roles, login, session
├── clients/                 clients, rates
├── routes/                  origin → destination lanes
├── requests/                service requests + lifecycle
├── trips/                   assignment, trips, deliveries, incidents
├── fleet/                   vehicles, maintenance, fuel
├── operators/               employees + licenses
├── finance/                 expenses, advances, invoices, payments
└── reports/                 report queries, CSV export, dashboard
```

Grouping by feature keeps everything about "trips" in one folder, so a change usually touches one
package. Each feature package follows the same shape: `Thing` · `ThingRepository` · `ThingService` ·
`ThingView`.

## Startup flow

```text
App.main
 ├─ FlatLightLaf.setup()          (optional look & feel)
 ├─ Database.testConnection()     (fail fast with a clear dialog)
 └─ App.start()
      ├─ LoginView.prompt()       → AuthService.login() → BCrypt + permissions
      ├─ Session.login(user)
      └─ new MainFrame()          tabs added only if Session.has(permission)
```

See [Security & users](/features/security/) for the permission model.

## Where to go deeper

- [The four layers](/architecture/layers/) — a vertical slice, file by file.
- [Database access](/architecture/database-access/) — `Database`, `Result`, error translation, ids.
- [UI toolkit](/architecture/ui-toolkit/) — `BaseView`, `FormPanel`, `ModalForm`, tables, async.
- [Conventions](/architecture/conventions/) — code style and the Git workflow.
