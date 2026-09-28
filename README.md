# SysPort - MARJAN

A web application to run the full lifecycle of a trucking operation: from a client's service request, through trip assignment, costs, delivery and invoicing, to management reports.

Built for the fictional (personal-project) company **Transportes MARJAN**, based on a real requirements-gathering interview.

> Status: in development. The original Java Swing client was revamped to a Vue PWA
> over a Micronaut API; the business rules still live in the MySQL database.
>
> 📚 **New to the codebase?** Read the local developer wiki in [`webdocs/`](webdocs/):
> `make docs` → <http://localhost:4321>. It covers architecture, the database, the
> business rules and a per-package guide.

---

## What it does

- **Clients & rates**: occasional/frequent clients, cash/credit terms, negotiated rates per route.
- **Service requests**: unique folio and a controlled lifecycle (`requested → authorized → scheduled → assigned → in_transit → delivered → closed`, or `cancelled`).
- **Trips & assignment**: assign an available vehicle and operator, with double-booking and license-expiry validation.
- **Costs**: expenses by type, driver advances and their settlement, fuel loads and fuel efficiency.
- **Fleet & operators**: vehicle status, maintenance history, licenses and expiry alerts.
- **Incidents & deliveries**: incident log per trip, proof of delivery, closing rules.
- **Invoicing & collections**: invoices, payments, outstanding balances, overdue detection.
- **Users & permissions**: roles, permissions, audit trail, no hard deletes.
- **Reports**: revenue per client, route usage, vehicle usage, fuel yield, profitability per trip; CSV export.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Java 21 (LTS) |
| API | Micronaut 4.10 (Netty, JWT, HikariCP) |
| Frontend | Vue 3 + Vite + TypeScript + Tailwind + shadcn-vue (Reka UI), PWA |
| Database | MySQL 8.4 (in Docker Compose) |
| Data access | Plain JDBC over stored procedures (no ORM) |
| Build | Maven (backend), npm (frontend), Docker |
| Tests | JUnit 5, Micronaut Test (opt-in against MySQL) |

## Architecture

```text
Browser (Vue PWA)  --JSON/JWT-->  api (Micronaut)  --JDBC-->  core services  -->  MySQL (rules)
```

- **core/** — UI-free domain: records, enums, repositories (stored-procedure calls) and services. The business rules live in `db/init/*.sql`.
- **api/** — REST controllers, JWT security, request-scoped identity, scheduled jobs.
- **frontend/** — Vue single-page app (installable PWA).
- **db/** — schema, functions, procedures, views and seed data (the single source of truth).
- **webdocs/** — the Astro/Starlight developer wiki.

## Quick start

**Prerequisites:** JDK 21, Maven 3.9+, Node 22+, Docker with Compose.

```bash
make setup     # create .env and install frontend deps
make db-up     # start MySQL (schema + seed load on first run)
make api       # build and run the API on http://localhost:8080
make web       # in another terminal: Vue dev server on http://localhost:5173
```

Then open <http://localhost:5173> and log in with the dev seed user `admin` / `admin123`
(**change it, dev only**).

Prefer Docker for everything? `make up` builds and starts MySQL, the API and the
web client (nginx) at <http://localhost:8081>.

Run `make help` for the full task list.

### Database commands

```bash
make db-up       # start
make db-down     # stop (data kept)
make db-reset    # stop AND wipe the volume (re-runs db/init)
```

> Scripts in `db/init/` only run when the data volume is empty. After changing the
> schema during development, reset with `make db-reset`.

### Tests

```bash
make test        # fast unit tests (no database)

# Integration tests against the real MySQL rules (opt-in):
make db-reset
make test-it     # or: SYSPORT_IT=1 mvn test
```

The business rules live in the database, so the integration tests exercise the
stored procedures and the HTTP API directly. They are skipped unless `SYSPORT_IT=1`,
and expect a freshly seeded database.

### Connection settings

Read from environment variables, with these defaults:

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/sysportdb` |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `localhost` / `3306` / `sysportdb` |
| `DB_USER` / `DB_PASSWORD` | `marjan` / `changeme` |
| `JWT_SECRET` / `JWT_REFRESH_SECRET` | dev defaults (change outside development) |

## Project structure

```
marjan/
├── Makefile
├── docker-compose.yml
├── .env.example
├── pom.xml                     # Maven aggregator (core + api)
├── db/
│   ├── build-bootstrap.sh       # concatenates db/init/*.sql into SYSPORT_MARJAN.sql
│   └── init/                    # loaded by Docker in filename order
│       ├── 01-tables.sql        # database and tables
│       ├── 02-functions.sql     # rule functions + id/folio allocators
│       ├── 05-views.sql         # shared read projections
│       ├── 10-security.sql      # users, roles, permissions, audit
│       └── ... domain scripts and 99-seed.sql
├── core/                        # records, repositories, services (no UI, no HTTP)
├── api/                         # Micronaut controllers, security, jobs
├── frontend/                    # Vue app (views, components, stores, router)
├── PRD.md                       # requirements, architecture, plan
└── webdocs/                     # developer wiki (Astro/Starlight)
```

Each feature slice follows the same shape: `Thing` (record) · `ThingRepository`
(stored-procedure calls) · `ThingService` (permissions + use cases) · a REST
controller in `api/` · a Vue `ThingView`.

## Design in one paragraph

Data is modeled as **immutable records**; **business rules live in the database**
as stored procedures and functions (the single source of truth); **repositories**
are thin JDBC wrappers that call those routines; **services** enforce permissions
and coordinate; the **API** exposes them over HTTP with JWT; the **Vue app** only
displays and collects input. Simple over clever.

## Contributing to your future self

- Small, atomic commits using [Conventional Commits](https://www.conventionalcommits.org/): `feat(trips): block vehicle double-booking`.
- Short-lived branches (`feat/…`, `fix/…`), merged into `main` when green.
- Every rule in the PRD (`BR-xx`) should have at least one test.

Details in the PRD, section *Git workflow*.
