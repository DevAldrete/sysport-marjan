# SysPort - MARJAN

A desktop application to run the full lifecycle of a trucking operation: from a client's service request, through trip assignment, costs, delivery and invoicing, to management reports.

Built for the fictional (personal-project) company **Transportes MARJAN**, based on a real requirements-gathering interview.

> Status: in development. See [`PRD.md`](PRD.md) for the full plan and milestones.

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
| UI | Java Swing (+ FlatLaf for a modern look, optional) |
| Database | MariaDB 11 (in Docker Compose; MySQL-compatible) |
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
mvn compile exec:java
```

Default dev login (from seed data): `admin` / `admin123` — **change it, dev only.**

### Database commands

```bash
docker compose up -d          # start
docker compose logs -f db     # view logs
docker compose down           # stop (data kept)
docker compose down -v        # stop AND wipe data (re-runs db/init scripts)
```

> Scripts in `db/init/` only run when the data volume is empty. After changing the schema in early development, use `docker compose down -v && docker compose up -d`.

### Connection settings

Read from environment variables, with these defaults:

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:mariadb://localhost:3306/sysportdb` |
| `DB_USER` | `marjan` |
| `DB_PASSWORD` | `changeme` |

## Project structure

```
marjan/
├── docker-compose.yml
├── .env.example
├── pom.xml
├── db/
│   └── init/
│       ├── 01-schema.sql        # tables, constraints
│       ├── 02-seed.sql          # roles, permissions, admin user, sample data
│       └── 03-demo-seed.sql     # large showcase dataset (50 routes, 70 clients/operators/vehicles, demo requests)
├── PRD.md                      # requirements, architecture, plan
└── src/
    ├── main/java/mx/marjan/
    │   ├── App.java             # entry point
    │   ├── shared/              # db, Result, UI base classes, utils
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

Each feature package follows the same shape: `Thing` (record) · `ThingRepository` (JDBC) · `ThingService` (use cases) · `ThingRules` (pure functions) · `ThingView` (Swing).

## Design in one paragraph

Data is modeled as **immutable records**; business rules are **pure functions** over those records (easy to test without a database or UI); **repositories** are the only place with SQL; **services** open transactions and coordinate; **views** only display and collect input. Simple over clever.

## Contributing to your future self

- Small, atomic commits using [Conventional Commits](https://www.conventionalcommits.org/): `feat(trips): block vehicle double-booking`.
- Short-lived branches (`feat/…`, `fix/…`), merged into `main` when green.
- Every rule in the PRD (`BR-xx`) should have at least one test.

Details in the PRD, section *Git workflow*.
