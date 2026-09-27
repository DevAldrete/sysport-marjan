# SysPort - MARJAN

A desktop application to run the full lifecycle of a trucking operation: from a client's service request, through trip assignment, costs, delivery and invoicing, to management reports.

Built for the fictional (personal-project) company **Transportes MARJAN**, based on a real requirements-gathering interview.

> Status: in development. See [`PRD.md`](PRD.md) for the full plan and milestones.
>
> 📚 **New to the codebase?** Read the local developer wiki in [`webdocs/`](webdocs/):
> `cd webdocs && npm install && npm run dev` → <http://localhost:4321>. It covers architecture,
> the database, the business rules and a per-package guide.

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
| UI | JavaFX 23 + [AtlantaFX](https://github.com/mkpaz/atlantafx) (Primer theme) + Ikonli icons |
| Database | MySQL 8.4 (in Docker Compose) |
| Data access | Plain JDBC (no ORM) |
| Build | Maven |
| Tests | JUnit 5 |

## Quick start

**Prerequisites:** JDK 21, Maven 3.9+, Docker with Compose.

```bash
# 1. Clone
git clone https://github.com/DevAldrete/sysport-marjan marjan && cd marjan

# 2. Configure environment
cp .env.example .env

# 3. Start the database (schema and seed data load automatically on first run)
docker compose up -d

# 4. Run the app
mvn javafx:run
```

Default dev login (from seed data): `admin` / `admin123` — **change it, dev only.**

### Database commands

```bash
docker compose up -d          # start
docker compose logs -f mysql  # view logs
docker compose down           # stop (data kept)
docker compose down -v        # stop AND wipe data (re-runs db/init scripts)
```

> Scripts in `db/init/` only run when the data volume is empty. After changing the schema in early development, use `docker compose down -v && docker compose up -d`.

### Tests

```bash
mvn test                              # fast unit tests (no database)

# Integration tests against the real MySQL rules (opt-in):
docker compose down -v && docker compose up -d
SYSPORT_IT=1 mvn test                 # or mvn test -Dtest=SqlRulesTest
```

The business rules live in the database, so the integration tests exercise the
stored procedures directly (`SqlRulesTest`). They are skipped unless
`SYSPORT_IT=1`, and expect a freshly seeded database.

### Connection settings

Read from environment variables, with these defaults:

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/sysportdb` |
| `DB_USER` | `marjan` |
| `DB_PASSWORD` | `changeme` |

## Project structure

```
marjan/
├── docker-compose.yml
├── .env.example
├── pom.xml
├── db/
│   ├── build-bootstrap.sh      # concatenates db/init/*.sql into SYSPORT_MARJAN.sql
│   └── init/                   # loaded by Docker in filename order
│       ├── 01-tables.sql       # database and tables
│       ├── 02-functions.sql    # rule functions + id/folio allocators
│       ├── 05-views.sql        # shared read projections
│       ├── 10-security.sql     # users, roles, permissions, audit
│       ├── 20-clients.sql      # clients, rates, routes
│       ├── 30-fleet.sql        # vehicles, fuel, maintenance
│       ├── 40-operators.sql    # employees, licences
│       ├── 50-requests.sql     # service requests + lifecycle actions
│       ├── 60-trips.sql        # assignment, trips, deliveries, incidents
│       ├── 70-costs.sql        # expenses, advances
│       ├── 80-finance.sql      # invoices, payments
│       ├── 90-reports.sql      # report and dashboard queries
│       └── 99-seed.sql         # roles, permissions, admin user, demo rows
├── PRD.md                      # requirements, architecture, plan
└── src/
    ├── main/java/mx/marjan/
    │   ├── App.java             # entry point (JavaFX Application)
    │   ├── ui/                  # JavaFX toolkit: AppShell, Navigation, tables, forms, theme
    │   ├── shared/              # db, Result, pure helpers (Money, Dates, FormModel), utils
    │   ├── security/            # users, roles, login
    │   ├── clients/             # clients, rates
    │   ├── requests/            # service requests
    │   ├── trips/               # trips, assignment, deliveries, incidents
    │   ├── fleet/               # vehicles, maintenance, fuel
    │   ├── operators/           # employees, licenses
    │   ├── finance/             # expenses, advances, invoices, payments
    │   └── reports/
    └── test/java/mx/marjan/
```

Each feature package follows the same shape: `Thing` (record) · `ThingRepository` (stored-procedure calls) · `ThingService` (permissions + use cases) · `ThingView` (JavaFX). The shared JavaFX building blocks live in `mx.marjan.ui`.

## Design in one paragraph

Data is modeled as **immutable records**; **business rules live in the database** as stored procedures and functions (the single source of truth); **repositories** are thin JDBC wrappers that call those routines; **services** enforce permissions and coordinate; **views** only display and collect input. Simple over clever.

## Contributing to your future self

- Small, atomic commits using [Conventional Commits](https://www.conventionalcommits.org/): `feat(trips): block vehicle double-booking`.
- Short-lived branches (`feat/…`, `fix/…`), merged into `main` when green.
- Every rule in the PRD (`BR-xx`) should have at least one test.

Details in the PRD, section *Git workflow*.
