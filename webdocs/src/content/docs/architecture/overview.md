---
title: Architecture overview
description: The layers, the one-way dependency rule, and why the rules live in the database.
---

## The one rule that explains everything

```text
   Vue view  →  REST controller (Micronaut)  →  service  →  repository (JDBC)  →  MySQL
                                                        ↓
                                                   records + enums (pure, no I/O)
```

Dependencies point **one way**. A view calls the API over HTTP; a controller delegates to a
service; a service calls a repository; a repository calls the database. Never the reverse.
This keeps changes local and testable.

## Modules

| Module | Responsibility |
| --- | --- |
| **core/** | UI-free domain: records, enums, repositories and services. No HTTP, no Swing. |
| **api/** | Micronaut controllers, DTOs, JWT security, request-scoped identity, scheduled jobs. |
| **frontend/** | Vue single-page app (views, components, stores, router). |
| **db/** | Schema, functions, procedures, views and seed data (the rule source of truth). |

## Layers and responsibilities

| Layer | Responsibility | Must **not** |
| --- | --- | --- |
| **records / enums** | Immutable data (`Client`, `Trip`, …) and typed statuses | Contain logic that needs I/O |
| **routines** (SQL) | Stored procedures & functions: validate, calculate, decide transitions | Live in Java |
| **repository** | Thin JDBC wrappers that call routines; map `ResultSet` ↔ records | Contain business decisions |
| **service** | One use case = one routine call; checks permission; adapts the outcome | Know about HTTP or the database driver |
| **controller** | Maps HTTP ↔ service; guards with `@Secured`; maps `Result` to status codes | Contain business rules |
| **view** | Vue views and components: display, collect input, call the API | Contain SQL or business rules |

## Why the rules live in the database

The database is the **single source of truth**. A rule such as *"a vehicle cannot be double-booked"*
is enforced by `sp_assign_trip` inside a transaction, not by Java. Consequences:

- Changing a rule changes a stored procedure — **no recompile**.
- Concurrency is correct: the rule runs with the row locks it needs.
- There is exactly one place to look and one place to test.
- Java keeps only the **typed vocabulary** (enums) and **transport** (repositories/services/controllers).

:::note
The enum's job is only to give the UI a typed, exhaustive set of values. The *transitions* between
them live in `fn_request_can_transition` (see [Lifecycles](/domain/lifecycle/)).
:::

## How a write becomes an HTTP response

```text
POST /api/requests/{id}/authorize
  → RequestController.authorize()            @Secured(requests.write)
    → ServiceRequestService.authorize()      caller.has(REQUESTS_WRITE)
      → ServiceRequestRepository.authorize()
        → Database.callVoid("{call sp_authorize_request(?,?,?,?)}", …)
          → MySQL: fn_request_can_transition + UPDATE + audit row
  ← Result.Err(List<String>) → 422 {problems:[…]}
  ← Result.Ok(value)         → 200 value
```

`Result.Err` carries the procedures' human problems; `Responses.of` turns it into a 422 with a
`problems` array. A global handler maps JDBC failures (`DataException`) to 500.

## Package layout

```text
mx.marjan
├── shared/       Database, Result, DataException, Money, Dates, Numbers, Text, Validators
├── security/     Caller, Permissions, CurrentUser, AuthService, User/Role
├── clients/      clients, rates
├── routes/       origin → destination lanes
├── requests/     service requests + packages
├── trips/        assignment, trips, deliveries, incidents
├── fleet/        vehicles, maintenance, fuel
├── operators/    employees + licenses
├── finance/      expenses, advances, invoices, payments
└── reports/      report queries, dashboard

mx.marjan.api
├── auth/         login/refresh/me/password helpers
├── clients|routes|operators|fleet|requests|trips|finance|reports|dashboard|users
├── security/     authentication provider, Callers, refresh persistence, system caller
├── jobs/         scheduled lifecycle sweep and invoice refresh
├── json/         enum ↔ dbValue Jackson module
└── error/        problem/DataException handlers
```

## Where to go deeper

- [The four layers](/architecture/layers/) — a vertical slice, file by file.
- [The API](/architecture/api/) — Micronaut, JWT, permissions, error mapping.
- [The web frontend](/architecture/web-frontend/) — Vue, router, stores, PWA.
- [Database access](/architecture/database-access/) — `Database`, `Result`, error translation, ids.
