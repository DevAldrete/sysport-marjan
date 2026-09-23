# PRD: SysPort - MARJAN - Transport Management System

| | |
|---|---|
| **Version** | 1.0 |
| **Source material** | Requirements interview (`SISTEMA-DE-GESTION-DE-TRANSPORTES-MARJAN.md`) |

---

## 1. Overview

### 1.1 Problem
Transportes MARJAN tracks operations across spreadsheets, printed documents and chat messages. The real difficulty is not recording trips; it is **connecting everything around a trip**: who requested it, what was carried, which vehicle and operator ran it, what it cost, whether it was delivered, and whether it was paid.

### 1.2 Product vision
A desktop application that models the **entire service lifecycle** so that any operation can be reconstructed end to end, and so the owner can answer: *Which clients make me the most money? Which routes and vehicles are most used? What did this trip really cost? Who owes me money?*

### 1.3 Goals
1. Register clients, service requests, trips, vehicles, operators, costs, deliveries, invoices and payments in one place.
2. Enforce the business rules automatically (no double-booking, no expired licenses, no assigning broken vehicles).
3. Preserve history: nothing important is physically deleted; past trips never change when current data changes.
4. Produce management reports with filters and CSV export.
5. Keep the codebase small, readable and easy to change.

### 1.4 Non-goals (v1)
- Web or mobile access, multi-branch, or multi-tenant support.
- GPS tracking, fleet telematics, route optimization.
- Electronic invoicing (CFDI/SAT stamping). Invoices here are internal records.
- Photo, document or e-signature capture (only a text `evidence_reference` in v1; the interview places these in a later phase).
- Payroll, accounting ledger, inventory of spare parts.

### 1.5 Hard constraints vs. preferences

| Type | Item |
|---|---|
| **Hard** | Java Swing UI |
| **Hard** | MariaDB (MySQL-compatible), run from Docker Compose |
| **Hard** | JDBC for data access |
| Preferred | Clean OOP combined with Data-Oriented Programming |
| Preferred | Low complexity, readability, maintainability |
| Preferred | Heavy, disciplined Git use with atomic, contextual commits |

---

## 2. Users and roles

| Role | Typical person | Can do |
|---|---|---|
| `admin` | Owner | Everything, including users, roles and reports |
| `traffic` | Dispatcher | Clients, requests, trips, assignment, deliveries, incidents, expenses/advances |
| `maintenance` | Fleet manager | Vehicles, maintenance, fuel, out-of-service marking |
| `collections` | Billing clerk | Invoices, payments, receivables reports |
| `viewer` | Read-only consult | View data and reports; no changes |

Permissions are fine-grained strings (e.g. `trips.assign`, `invoices.write`, `reports.view`) linked to roles through `role_permissions`. The UI hides or disables actions the user lacks; the **service layer also checks**, so the rules do not depend on the UI.

---

## 3. Architecture and engineering guidelines

### 3.1 Stack
- **Java 21**: records, sealed interfaces, pattern-matching `switch`, `Optional`. All are natural fits for DOP.
- **Maven** for build; dependencies kept minimal:
  - `org.mariadb.jdbc:mariadb-java-client` (JDBC driver, latest 3.x)
  - `jbcrypt` or equivalent BCrypt library (password hashing)
  - `FlatLaf` (optional, look and feel)
  - `JUnit 5` (test)
- **MariaDB 11** in Docker Compose, schema loaded from `db/init/`.

### 3.2 Layers (and the only allowed dependency direction)

```
   view (Swing)  →  service  →  repository (JDBC)  →  MariaDB
                       ↓
                  rules + records (pure, no I/O)
```

| Layer | Responsibility | Must not |
|---|---|---|
| **records** | Immutable data (`Client`, `Trip`, …) and enums for statuses | Contain logic that needs I/O |
| **rules** | Pure functions: `(data) → Result`. Validate, calculate, decide transitions | Touch DB, Swing or the clock (pass `LocalDate` in) |
| **repository** | The **only** place with SQL. Maps `ResultSet` ↔ records | Contain business decisions |
| **service** | A use case = one transaction. Loads data, calls rules, saves, checks permission | Know about Swing |
| **view** | Swing panels: display, collect input, call services on a background thread | Contain SQL or business rules |

### 3.3 How OOP and DOP combine (the practical rule)

- **Data is dumb and immutable → `record`.** `record Trip(long id, long serviceRequestId, …)`. "Changing" means creating a copy (`trip.withStatus(IN_TRANSIT)`).
- **Behavior lives in small, stateless, pure functions**, grouped in classes named after what they decide (`AssignmentRules`, `AdvanceRules`, `ServiceRequestFlow`).
- **Objects are used where they earn their keep:** services and repositories (they hold a connection/`DataSource` and represent capabilities), Swing components (inherently object-oriented).
- **Model states and outcomes explicitly** with enums and sealed types instead of strings, flags and exceptions for normal flow:

```java
public sealed interface Result<T> {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(List<String> problems) implements Result<T> {}
}

public enum RequestStatus {
    REQUESTED, AUTHORIZED, SCHEDULED, ASSIGNED, IN_TRANSIT, DELIVERED, CANCELLED, CLOSED;

    public boolean canMoveTo(RequestStatus next) { /* one switch, one place */ }
}
```

- **Rule of thumb:** if you can test it without a database or a window, it belongs in `rules`.

### 3.4 Package layout: by feature

```
mx.marjan
├── App.java
├── shared/     Database, Tx helper, Result, RecordTableModel<T>, BaseView, Money, Dates
├── security/   User, Role, Permission, AuthService, LoginView
├── clients/    Client, ClientRate, repos, service, views
├── requests/   ServiceRequest, RequestStatus, ServiceRequestFlow, …
├── trips/      Trip, Delivery, Incident, AssignmentRules, TripService, …
├── fleet/      Vehicle, Maintenance, FuelLoad, FuelRules, …
├── operators/  Employee, License, LicenseRules, …
├── finance/    Expense, Advance, Invoice, Payment, AdvanceRules, InvoiceRules, …
└── reports/    ReportRepository, ReportView, CsvExporter
```

Grouping by feature keeps everything about "trips" in one place, so a change usually touches one folder.

### 3.5 JDBC conventions
- Always `PreparedStatement` with `?` parameters. **Never** concatenate user input into SQL.
- Always try-with-resources on `Connection`, `PreparedStatement`, `ResultSet`.
- A tiny helper `Database.inTransaction(conn -> …)` sets `autoCommit=false`, commits on success, rolls back on any exception.
- Repositories accept a `Connection` (so several repositories can join one transaction) or get one via `Database`.
- Money is `BigDecimal` (never `double`) mapped to `DECIMAL(12,2)`.
- Dates use `java.time` (`LocalDate`, `LocalDateTime`); no `java.util.Date`.
- Start with `DriverManager` behind `Database`. Adding HikariCP later only changes that one class.
- No `AUTO_INCREMENT`: call `Sequences.next(connection, "table")` for the primary key inside the transaction. User input is validated with `Validators` (email, RFC, CURP, phone, plates, date ranges, non-negative amounts).
- Hand-written SQL with small `RowMapper`-style functions: `rs -> new Client(rs.getLong("id"), …)`.

### 3.6 Swing conventions
- **Never run DB work on the Event Dispatch Thread.** Use `SwingWorker` (wrapped in one helper: `Async.run(task, onSuccess, onError)`).
- One generic `RecordTableModel<T>` (columns defined as lambdas) instead of one `TableModel` per entity.
- Standard screen shape: filter bar → table → action buttons; edit in a modal `JDialog` form.
- Show validation problems from `Result.Err` in one consistent dialog/label; do not scatter `JOptionPane` calls.
- Main window: `JFrame` with a `JTabbedPane` or side menu; menu items shown according to permissions.

### 3.7 Code quality rules
- Methods short enough to read without scrolling; one level of abstraction per method.
- Names in **English**, domain terms consistent with the glossary (§14).
- No inheritance hierarchies deeper than one level; prefer composition and small interfaces.
- No comments that restate code; comment **why** (especially business rules, cite `BR-xx`).
- No dead code, no speculative abstractions ("we might need it later").
- Configuration (DB URL, credentials) from environment variables, never hard-coded, never committed.

---

## 4. Data model

The ER design in the SQL/PDF is a solid base. It maps well to the interview. Before using it, apply the corrections below. These are important because, as written, the script **will not create a correct database**.

### 4.1 Required fixes to the provided SQL

| # | Issue | Fix |
|---|---|---|
| F1 | **Foreign keys are inverted** (diagram-export artifact). `employees.id → users.employee_id`, `licenses.id → employees.license_id`, `service_requests.id → trips.service_request_id`, `trips.id → deliveries.trip_id` make the parent reference the child, creating impossible/circular constraints. | The **child** column references the **parent** `id`: `users.employee_id → employees.id`, `employees.license_id → licenses.id`, `trips.service_request_id → service_requests.id`, `deliveries.trip_id → trips.id`. Keep the `UNIQUE` on those columns to preserve the 1:1. |
| F2 | **`decimal` with no precision** defaults to `DECIMAL(10,0)` in MariaDB, so **cents are silently rounded away**. | Money: `DECIMAL(12,2)`. Quantities: liters `DECIMAL(8,2)`, price/liter `DECIMAL(8,3)`, km/weight/odometer `DECIMAL(10,1)`. |
| F3 | No key generation. | **No `AUTO_INCREMENT`.** Ids are `BIGINT PRIMARY KEY` (matching `BIGINT` FKs) allocated by the application through the `sequences` table inside the same transaction (`Sequences.next`). |
| F4 | Status columns are free text. | Add `CHECK (status IN (...))` constraints (MariaDB ≥ 10.2 enforces them) and mirror them as Java enums. |
| F5 | Missing `NOT NULL`/defaults. | `created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP`; `NOT NULL` where the domain demands it. |
| F6 | `licenses.updated_at` exists but no other table has one. | Keep only where edits are expected (`licenses`, `service_requests`, `trips`, `vehicles`, `employees`). |
| F7 | Status vocabulary mixes Spanish (`solicitada`…) and English. | Use English codes everywhere (see §5). Display labels can be localized in the UI. |
| F8 | No index on frequent filters. | Index FKs and search columns: `trips(vehicle_id, planned_start)`, `trips(employee_id, planned_start)`, `service_requests(status)`, `invoices(status, due_date)`. |

### 4.2 Recommended additions (gaps vs. the interview)

| # | Gap | Addition |
|---|---|---|
| A1 | Interview requires **contact person** and commercial conditions for clients | `clients.contact_name`, `clients.credit_limit DECIMAL(12,2)`, `clients.credit_days INT` |
| A2 | Incidents need **"actions taken"** | `incidents.actions_taken VARCHAR(500)` |
| A3 | Double-booking checks need a **planned time window** on the trip (actual times don't exist yet when assigning) | `trips.planned_start`, `trips.planned_end` (copied from the request's scheduled dates at assignment) |
| A4 | Operators can be **incapacitated** (interview), schema has `disabled` | Employee status: `available, on_trip, resting, vacation, incapacitated, terminated` |
| A5 | "Important operations record who and when" | `created_by` / `updated_by` (FK → `users`) on `service_requests`, `trips`, `expenses`, `advances`, `payments`, `invoices`, `maintenance`, `deliveries`. Optionally a light `audit_log(id, user_id, entity, entity_id, action, created_at)`. |
| A6 | Trip and delivery statuses have no defined values | See §5 |
| A7 | Some services need **required documentation before closing** | `service_requests.requires_documents BOOLEAN` (default true) |
| A8 | Advance settlement outcome | Derived by query (§8.3), not stored. Add `advances.settled_at` and `settled_by` only. |
| A9 | Fuel double counting between `expenses` and `fuel_loads` | See decision D3 (§13) |
| A10 | `service_requests.route_id` is nullable, yet origin/destination are mandatory business data | Make `route_id NOT NULL`; ad-hoc destinations simply create a new route record (decision D2) |

### 4.3 Entity overview

```
roles ─< role_permissions >─ permissions
roles ─< users >─ employees ─ licenses            (employees = operators + staff)
clients ─< client_rates >─ routes
clients ─< service_requests >─ routes
service_requests ─ 1:1 ─ trips >─ vehicles
                          trips >─ employees
trips ─< expenses          trips ─< advances
trips ─< incidents         trips ─ 1:1 ─ deliveries
vehicles ─< fuel_loads >─ trips (optional)
vehicles ─< maintenance
service_requests ─< invoices ─< payments
```

**Snapshot principle:** history must never change because current data changes. `service_requests.agreed_rate` stores the price at authorization; the trip stores its vehicle/operator IDs permanently. Do **not** "update" a trip's vehicle to reflect a later reassignment without recording the change (see BR-15).

---

## 5. State machines

### 5.1 Service request (`service_requests.status`)

```
requested ─► authorized ─► scheduled ─► assigned ─► in_transit ─► delivered ─► closed
    │            │             │            │
    └────────────┴─────────────┴────────────┴──► cancelled   (allowed until in_transit)
```

| Transition | Trigger | Condition |
|---|---|---|
| requested → authorized | User authorizes | `agreed_rate` is set and > 0 |
| authorized → scheduled | Pickup/delivery dates confirmed | Both scheduled dates present, delivery after pickup |
| scheduled → assigned | Trip created | Passes assignment validation (BR-05…BR-09) |
| assigned → in_transit | Departure recorded | `departure_datetime` set |
| in_transit → delivered | Delivery registered | Delivery record exists |
| delivered → closed | Close | Documentation requirement met (BR-13) |
| any pre-transit → cancelled | User cancels | Reason in `notes`; frees vehicle/operator |

Only forward moves listed above (plus cancel) are valid. Encode this in `RequestStatus.canMoveTo`.

### 5.2 Trip
`scheduled → in_transit → completed`, or `cancelled`. (The trip is created at assignment.)

### 5.3 Vehicle
`available ⇄ assigned ⇄ on_trip`; `maintenance` and `out_of_service` set manually by maintenance role; `decommissioned` is terminal.

### 5.4 Operator (employee)
`available ⇄ on_trip`; `resting`, `vacation`, `incapacitated` set manually; `terminated` is terminal.

### 5.5 Delivery
`pending_documents → complete`.

### 5.6 Invoice
`pending → paid`; `pending → overdue` (when past due and unpaid); `pending/overdue → cancelled`. Status is **derived** where possible (see BR-19).

> **Availability rule of thumb:** the stored status covers manual conditions (maintenance, vacation…). *Whether someone is free for a given date range* is decided by **checking overlapping trips**, not by trusting a flag alone (BR-05/06).

---

## 6. Business rules

Each rule gets an ID so code, tests and commits can reference it.

| ID | Rule | Enforced in |
|---|---|---|
| BR-01 | Every service request gets a unique folio (e.g. `SR-2026-000123`). | DB `UNIQUE` + `FolioGenerator` |
| BR-02 | Client RFC is unique; RFC format validated. | DB + `ClientRules` |
| BR-03 | Service request follows the state machine in §5.1; invalid transitions are rejected. | `RequestStatus`, service |
| BR-04 | `agreed_rate` is a snapshot set at authorization and never recalculated from general tariffs. Suggested from `client_rates` (valid on the date) but editable at authorization. | `RateRules` |
| BR-05 | A **vehicle** cannot be in two trips with overlapping planned windows (`scheduled`/`in_transit`). | `AssignmentRules` + tx |
| BR-06 | An **operator** cannot be in two trips with overlapping planned windows. | `AssignmentRules` + tx |
| BR-07 | Vehicle must be assignable: not `maintenance`, `out_of_service`, `decommissioned`. | `AssignmentRules` |
| BR-08 | Vehicle load capacity ≥ request's estimated weight. | `AssignmentRules` |
| BR-09 | Operator must be assignable (`available`, not on vacation/incapacitated/terminated) and have a license valid **through the planned end date**. | `AssignmentRules`, `LicenseRules` |
| BR-10 | Licenses expiring within 30 days raise a warning; expired ones block assignment. | `LicenseRules` |
| BR-11 | A vehicle marked `out_of_service` cannot be assigned until returned to `available`. | `AssignmentRules` |
| BR-12 | Each trip has at most one delivery. | DB `UNIQUE(trip_id)` |
| BR-13 | A request requiring documents cannot move to `closed` until its delivery is `complete` (has `received_by` and `evidence_reference`). | `ClosingRules` |
| BR-14 | The normal lifecycle uses status (`cancelled`, `terminated`, `decommissioned`, `disabled`) to keep history. An explicit, confirmed **hard delete** is also available; the database still refuses to delete a parent that has related rows. | Repos expose `delete(id)`; services check permission and the UI asks for confirmation |
| BR-15 | Changing the vehicle/operator on an already-created trip is allowed only before `in_transit`, is validated like a new assignment, and is logged in the audit trail. | `TripService` |
| BR-16 | Advance balance = `amount_given − Σ expenses (+ fuel, see D3)` of that trip: positive → operator returns money; negative → company reimburses; zero → settled. | `AdvanceRules` |
| BR-17 | Expense type must be one of the allowed values; amount > 0. | `ExpenseRules` + CHECK |
| BR-18 | Fuel load: `amount ≈ liters × price_per_liter` (tolerance ±0.05); odometer never decreases for a vehicle; the trip's vehicle must match the load's vehicle. | `FuelRules` |
| BR-19 | Invoice `paid` when Σ payments ≥ amount; `overdue` when `due_date < today` and unpaid. Payments cannot exceed the outstanding balance. Cash clients: `due_date = issue_date`; credit clients: `issue_date + credit_days`. | `InvoiceRules` |
| BR-20 | One invoice per service request in v1; only for requests in `delivered` or `closed`. | `InvoiceRules` |
| BR-21 | Maintenance record updates `vehicles.mileage` if its odometer is higher; completing a trip updates mileage by `actual_km`. | services |
| BR-22 | Every create/update of key operations stores `created_by`/`updated_by`. | services |
| BR-23 | Users need a permission for each service operation; disabled users cannot log in. | `AuthService` |
| BR-24 | Passwords are stored only as BCrypt hashes; never logged. | `AuthService` |

---

## 7. Functional requirements

Priority: **P0** = MVP essential · **P1** = important · **P2** = nice to have.

### 7.1 Security and users
| ID | Requirement | Pri |
|---|---|---|
| FR-SEC-1 | Login screen; authenticate against `users` (BCrypt); block `disabled` | P0 |
| FR-SEC-2 | Role → permissions loaded at login; menus/buttons reflect permissions | P0 |
| FR-SEC-3 | Admin CRUD for users, role assignment, enable/disable | P1 |
| FR-SEC-4 | Change own password | P1 |
| FR-SEC-5 | Audit log of important operations | P2 |

### 7.2 Clients and rates
| ID | Requirement | Pri |
|---|---|---|
| FR-CLI-1 | Create/edit/search clients (name, RFC, address, phone, email, contact, type, payment terms, credit data) | P0 |
| FR-CLI-2 | Deactivate instead of delete | P0 |
| FR-CLI-3 | Manage negotiated rates per client/route with validity dates | P1 |
| FR-CLI-4 | Client detail: request history, outstanding balance | P1 |

### 7.3 Routes
| ID | Requirement | Pri |
|---|---|---|
| FR-RTE-1 | CRUD routes (origin, destination, estimated km, description) | P0 |
| FR-RTE-2 | Route stats: trips count, avg duration, avg cost, avg profit | P2 |

### 7.4 Service requests
| ID | Requirement | Pri |
|---|---|---|
| FR-REQ-1 | Create request with auto folio, client, route, cargo, weight, dates, notes | P0 |
| FR-REQ-2 | Suggest rate from `client_rates`; authorize with `agreed_rate` snapshot | P0 |
| FR-REQ-3 | Move through lifecycle with only valid transitions; cancel with reason | P0 |
| FR-REQ-4 | List with filters: folio, client, status, date range | P0 |
| FR-REQ-5 | Request detail view showing the whole story (trip, costs, delivery, invoice) | P1 |

### 7.5 Vehicles, maintenance, fuel
| ID | Requirement | Pri |
|---|---|---|
| FR-VEH-1 | CRUD vehicles (internal code, plates, brand, model, year, serial, type, capacity, mileage); status changes | P0 |
| FR-VEH-2 | Vehicle history: all trips it participated in | P1 |
| FR-MNT-1 | Register maintenance (preventive/corrective) with next service date/km | P1 |
| FR-MNT-2 | Mark vehicle `out_of_service` / back to `available` | P0 |
| FR-MNT-3 | "Maintenance due" list (by date or km) | P1 |
| FR-FUEL-1 | Register fuel loads (vehicle, trip, station, liters, price, amount, odometer) | P1 |
| FR-FUEL-2 | Fuel efficiency: km per liter per vehicle/trip/period | P1 |

### 7.6 Operators and licenses
| ID | Requirement | Pri |
|---|---|---|
| FR-OPR-1 | CRUD employees/operators with personal, emergency-contact and license data | P0 |
| FR-OPR-2 | Manage operator status (vacation, incapacitated, resting…) | P0 |
| FR-OPR-3 | Expiring/expired licenses list and dashboard warning | P1 |
| FR-OPR-4 | Operator history: trips performed | P2 |

### 7.7 Trips and assignment
| ID | Requirement | Pri |
|---|---|---|
| FR-TRP-1 | From a `scheduled` request, list only **eligible** vehicles and operators for the planned window | P0 |
| FR-TRP-2 | Create trip; run all validations BR-05…BR-11 in one transaction; show all problems at once | P0 |
| FR-TRP-3 | Record departure and arrival (date/time, actual km) | P0 |
| FR-TRP-4 | Reassign before departure (BR-15) | P1 |
| FR-TRP-5 | Register incidents (type, date, time, location, description, actions) | P1 |
| FR-TRP-6 | Trip detail: costs, fuel, advances, incidents, delivery, margin | P1 |

### 7.8 Costs and advances
| ID | Requirement | Pri |
|---|---|---|
| FR-EXP-1 | Register expenses by type per trip | P0 |
| FR-ADV-1 | Register advance to an operator for a trip | P1 |
| FR-ADV-2 | Show advance settlement: given vs. proven → return / reimburse / settled | P1 |
| FR-ADV-3 | Mark advance `settled` (with who/when) | P1 |

### 7.9 Deliveries
| ID | Requirement | Pri |
|---|---|---|
| FR-DEL-1 | Register delivery: actual date/time, received by, evidence reference | P0 |
| FR-DEL-2 | Close request only when BR-13 is met | P0 |

### 7.10 Invoicing and collections
| ID | Requirement | Pri |
|---|---|---|
| FR-INV-1 | Create invoice from a delivered request (amount defaults to `agreed_rate`) | P0 |
| FR-INV-2 | Register payments; partial payments allowed; outstanding balance shown | P0 |
| FR-INV-3 | Auto-derive `paid` / `overdue` | P1 |
| FR-INV-4 | Receivables list: by client, overdue, aging | P1 |

### 7.11 Reports and dashboard
| ID | Requirement | Pri |
|---|---|---|
| FR-RPT-1 | Revenue by client (period filter) | P1 |
| FR-RPT-2 | Most-used routes | P1 |
| FR-RPT-3 | Trips per vehicle; fuel consumption per vehicle | P1 |
| FR-RPT-4 | **Trip profitability**: `agreed_rate − expenses − fuel` | P0 |
| FR-RPT-5 | Clients with debt; overdue invoices | P1 |
| FR-RPT-6 | Available vehicles, vehicles near maintenance, licenses near expiry | P1 |
| FR-RPT-7 | Export any report to CSV | P1 |
| FR-DSH-1 | Home dashboard with alert counters (expiring licenses, overdue invoices, maintenance due, requests awaiting assignment) | P2 |

---

## 8. Key logic in detail

### 8.1 Assignment validation (the heart of the system)

```java
// AssignmentRules: pure, no I/O. Everything it needs is passed in.
static Result<AssignmentPlan> validate(
        ServiceRequest request,
        Vehicle vehicle, List<Trip> vehicleTripsInWindow,
        Employee operator, License license, List<Trip> operatorTripsInWindow,
        LocalDate today)
```

Checks, collecting **all** problems: request is `scheduled`; vehicle assignable (BR-07/11); capacity (BR-08); vehicle free (BR-05); operator assignable and licensed through the window (BR-09/10); operator free (BR-06).

`TripService.assign(...)` flow inside **one transaction**:
1. Lock the two candidate rows: `SELECT … FROM vehicles WHERE id=? FOR UPDATE` and same for the employee (prevents two dispatchers assigning the same resource at once).
2. Load overlapping trips:
   ```sql
   SELECT * FROM trips
   WHERE vehicle_id = ? AND status IN ('scheduled','in_transit')
     AND planned_start < ? AND planned_end > ?;
   ```
   (repeat for `employee_id`)
3. Call `AssignmentRules.validate(...)`.
4. If `Ok`: insert trip, move request → `assigned`, save `created_by`. If `Err`: rollback, return problems to the UI.

### 8.2 Eligible resources query (for the assignment dialog)
Vehicles with an assignable status **and** no overlapping active trip; operators with `available` status, valid license through `planned_end`, and no overlapping trip. Compute in SQL for the list, then re-validate with the rules on submit (never trust the list alone).

### 8.3 Advance settlement

```
balance = amount_given − (Σ expenses.amount + Σ fuel_loads.amount) for the trip
balance > 0 → operator must return balance
balance < 0 → company must reimburse |balance|
balance = 0 → settled
```

Model as a record: `record AdvanceBalance(BigDecimal given, BigDecimal proven, Outcome outcome)` with `sealed interface Outcome { Settled, OperatorOwes(amount), CompanyOwes(amount) }` and a pattern-matching `switch` in the view.

### 8.4 Profitability

```sql
SELECT sr.folio, sr.agreed_rate AS revenue,
       COALESCE(e.total,0) + COALESCE(f.total,0) AS cost,
       sr.agreed_rate - COALESCE(e.total,0) - COALESCE(f.total,0) AS margin
FROM service_requests sr
JOIN trips t ON t.service_request_id = sr.id
LEFT JOIN (SELECT trip_id, SUM(amount) total FROM expenses GROUP BY trip_id) e ON e.trip_id = t.id
LEFT JOIN (SELECT trip_id, SUM(amount) total FROM fuel_loads GROUP BY trip_id) f ON f.trip_id = t.id;
```

(Aggregate in subqueries to avoid the classic join-multiplication bug.)

### 8.5 Fuel efficiency
`km per liter = (max(odometer) − min(odometer)) / Σ liters` over a period per vehicle, or `actual_km / Σ liters` per trip. Flag trips deviating more than X% from the vehicle's average (P2).

---

## 9. Screens (UI inventory)

| Screen | Content |
|---|---|
| Login | user, password |
| Main window | menu by permission, dashboard tab |
| Clients | table + form dialog + rates tab |
| Routes | table + form |
| Service Requests | filter bar, table, actions (authorize, schedule, assign, cancel, close), detail dialog |
| Assign Trip (dialog) | eligible vehicles + operators, validation result panel |
| Trips | table, departure/arrival, expenses, advances, fuel, incidents tabs |
| Delivery | form, evidence reference |
| Vehicles | table, status actions, history, maintenance tab |
| Operators | table, form with license section, expiry highlight |
| Fuel loads | table + form |
| Invoices & Payments | invoice table, payment dialog, balance |
| Reports | report picker, filters, table, **Export CSV** |
| Users & Roles (admin) | user table, role/permission assignment |

UX basics: keyboard-friendly forms (Tab order, Enter to submit), colored status labels, expiring items highlighted, confirm dialogs on cancel/terminate actions.

---

## 10. Non-functional requirements

| Area | Requirement |
|---|---|
| **Simplicity** | New developer (you in 6 months) finds any rule by searching its `BR-xx` |
| **Integrity** | FK, UNIQUE, NOT NULL, CHECK enforce what the DB can; services enforce the rest inside transactions |
| **Performance** | Common screens load in < 1 s with ~50k trips; index FKs and filter columns; paginate large tables |
| **Responsiveness** | No DB call on the EDT |
| **Security** | BCrypt, prepared statements, secrets in env vars, least-privilege DB user for the app |
| **Portability** | Runs on Windows/macOS/Linux with JDK 21 + Docker |
| **Recoverability** | Documented `mysqldump` backup/restore commands in the README (P2) |
| **Testability** | Rules testable without DB/UI; repositories testable against a test DB |
| **Localization** | Code in English; UI labels can be Spanish (centralize strings in one `Labels` class or a properties file) |

---

## 11. Testing strategy

| Level | What | How |
|---|---|---|
| **Unit** (most tests) | `rules` and state machines: BR-03…BR-20 | JUnit 5, plain records in, `Result` out; no mocks needed |
| **Integration** | Repositories and transactions (double-booking under concurrency, rollback) | Real MariaDB from compose, separate `marjan_test` database, schema reset per test class |
| **Manual/UI** | Screen flows | A short checklist per milestone (§12 acceptance) |

Test naming: `AssignmentRulesTest.rejectsVehicleWithOverlappingTrip()`. **Every BR gets at least one test**, and the commit that adds a rule includes its test.

---

## 12. Implementation plan

Each milestone ends with a working, runnable app and a Git tag. Build **vertical slices**: schema + record + repo + service + view for one feature, then move on.

### M0: Project skeleton (v0.1.0)
- Git init, `.gitignore` (`target/`, `.env`, IDE files), `README`, `PRD` in `docs/`
- Maven project (Java 21), dependencies, `App.java` opening an empty `JFrame`
- `docker-compose.yml`, `.env.example`, `db/init/01-schema.sql` (**corrected schema, §4**), `02-seed.sql` (roles, permissions, admin)
- `Database` class + smoke test: app shows "connected" on start

**Done when:** `docker compose up -d && mvn compile exec:java` opens a window and connects.

### M1: Foundations & security (v0.2.0)
- `Result`, `Database.inTransaction`, `Async.run`, `RecordTableModel<T>`, `BaseView`
- BCrypt login, `AuthService`, session (`CurrentUser` record with permissions), permission-aware menu
- Tests: password check, permission check

**Done when:** admin logs in; a `viewer` sees no write actions; a disabled user cannot log in.

### M2: Master data (v0.3.0)
Build in this order, one vertical slice each: **Clients → Routes → Client rates → Operators + Licenses → Vehicles**.
- Rules: RFC format, license expiry (`LicenseRules`), status vocabularies as enums
- Tests: BR-02, BR-10

**Done when:** all five can be created, edited, searched and deactivated (no delete); expiring licenses are highlighted.

### M3: Service requests (v0.4.0)
- `RequestStatus` + `ServiceRequestFlow`, folio generator, rate suggestion, list with filters, authorize/schedule/cancel
- Tests: BR-01, BR-03, BR-04 (all valid/invalid transitions)

**Done when:** a request travels `requested → scheduled`, invalid transitions are refused, `agreed_rate` stays unchanged after editing a client rate.

### M4: Trips & assignment (v0.5.0), highest-risk milestone
- `planned_start/end` on trips, `AssignmentRules`, `TripService.assign` with row locking
- Eligible-resources queries, assignment dialog, departure/arrival recording, reassignment
- Tests: BR-05…BR-11, BR-15 (unit + one concurrent-assignment integration test)

**Done when:** it is impossible (even with two app instances) to double-book a vehicle or operator; all validation problems display together.

### M5: Costs (v0.6.0)
- Expenses, advances + settlement (`AdvanceRules`), fuel loads (`FuelRules`)
- Tests: BR-16, BR-17, BR-18

**Done when:** a trip shows total cost and the advance settlement outcome correctly.

### M6: Fleet care, incidents, deliveries (v0.7.0)
- Maintenance (+ due list), out-of-service flow, incidents, deliveries, closing rules
- Tests: BR-11, BR-13, BR-21

**Done when:** a request can only close with a complete delivery; an out-of-service vehicle never appears as eligible.

### M7: Invoicing & collections (v0.8.0)
- Invoices, payments, derived status, receivables view
- Tests: BR-19, BR-20

**Done when:** partial payments work, overpayment is rejected, overdue invoices are detected.

### M8: Reports & dashboard (v0.9.0)
- Report queries in `ReportRepository`, `CsvExporter`, report screen with period filters, alert dashboard
- Verify profitability against a hand-calculated seed scenario

**Done when:** all P0/P1 reports run with filters and export to CSV.

### M9: Hardening & release (v1.0.0)
- Audit fields everywhere (BR-22), optional `audit_log`, admin users/roles screens
- Review empty states, error messages, keyboard navigation
- Executable JAR (`maven-shade-plugin` or `jpackage`), backup/restore notes, final README pass

**Done when:** a full scenario runs without touching the DB directly: *client → request → authorize → assign → depart → expenses/fuel → deliver → close → invoice → pay → report.*

### Suggested seed data (for demos and tests)
3 clients (1 credit, 2 cash), 4 routes, 5 vehicles (one in maintenance), 5 operators (one with an expired license, one on vacation), 6 requests across different states, 2 invoices (one overdue).

---

## 13. Design decisions (log)

| ID | Decision | Reason |
|---|---|---|
| D1 | Plain JDBC + hand-written SQL, no ORM | Hard requirement; SQL stays visible and simple |
| D2 | `service_requests.route_id` is NOT NULL; ad-hoc routes are created as routes | One consistent way to know origin/destination |
| D3 | **Fuel cost lives only in `fuel_loads`.** The `fuel` option is removed from the expenses UI; cost reports and advance settlement add both tables | Avoids double counting without duplicating rows |
| D4 | One trip per request, one invoice per request in v1 | Matches the provided model; keep it simple; can be relaxed later |
| D5 | Availability = overlap check on trips, stored status only for manual conditions | A status flag alone gets stale |
| D6 | Status columns → Java enums + DB CHECK | Prevents typos, enables exhaustive `switch` |
| D7 | Soft delete via status for normal lifecycle, plus an explicit confirmed hard delete | Keep history by default; allow cleanup when truly needed (BR-14) |
| D8 | Invoice `paid`/`overdue` derived from payments and dates | Avoids inconsistent stored state |
| D9 | Feature-based packages | Changes stay local |

## 14. Glossary

| Term | Meaning |
|---|---|
| Service request | Client's order to move cargo from A to B; identified by folio |
| Trip | The physical execution of a request with a vehicle and an operator |
| Operator | Employee who drives (an `employee` with a license) |
| Advance | Cash given to an operator before a trip, to be justified afterwards |
| Settlement (comprobación) | Comparing the advance against proven expenses |
| Folio | Human-readable unique request identifier |
| Snapshot | A value copied at a point in time so it doesn't change later (e.g. `agreed_rate`) |
| DOP | Data-Oriented Programming: immutable data + pure functions over it |

## 15. Risks and open questions

| Risk / question | Mitigation |
|---|---|
| Concurrency: two users assigning the same vehicle | Transaction + `FOR UPDATE` + overlap check (M4) with a dedicated test |
| Swing UI takes the most time | Generic table model, standard screen shape, one base view; do not polish until M9 |
| Scope creep | Anything not in this PRD goes to a "later" list; P2 items only after M8 |
| Should one request ever have multiple trips (e.g. a breakdown mid-route)? | v1: no (D4). Revisit if needed: would need to drop `UNIQUE(service_request_id)` |
| Can vehicles carry multiple requests in one trip (consolidation)? | Out of scope v1 |
| Tax data (IVA) on invoices? | Out of scope v1: `amount` is the total. Add `subtotal`/`tax` later if needed |

---

## 16. Git workflow

### 16.1 Branching
- `main` is always runnable. Short-lived branches from `main`: `feat/trip-assignment`, `fix/fuel-odometer-check`, `docs/prd-update`, `chore/maven-setup`.
- Merge with a **squash or rebase** only if it keeps atomic commits meaningful; otherwise merge normally. Delete the branch after merging.
- Tag each milestone: `v0.1.0` … `v1.0.0`.

### 16.2 Commit format: Conventional Commits

```
<type>(<scope>): <imperative summary, ≤ 72 chars>

<optional body: WHY, not what. Reference rules, e.g. "Implements BR-05">
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `perf`, `style`.
Scopes: package names (`trips`, `finance`, `security`, `shared`, `db`, …).

### 16.3 What "atomic and contextual" means here
- **One logical change per commit.** It compiles, tests pass, and it could be reverted alone.
- **Never mix** refactoring with behavior changes, or formatting with logic.
- **A rule and its test go in the same commit.**
- **Schema changes are their own commit** (`feat(db): add planned window to trips`) so history shows why a table changed.
- Use `git add -p` to stage hunks; review with `git diff --staged` before every commit.
- Commit early and often locally; clean up (`git rebase -i`) before merging if needed.

### 16.4 Example history for one slice (Clients)

```
feat(db): add clients and client_rates tables
feat(clients): add Client and ClientRate records
feat(clients): add ClientRepository with JDBC CRUD
test(clients): cover RFC validation (BR-02)
feat(clients): validate RFC format in ClientRules
feat(clients): add ClientService with permission checks
feat(shared): add generic RecordTableModel
feat(clients): add clients table view and form dialog
docs: mark clients slice done in PRD milestones
```

### 16.5 Housekeeping
- `.gitignore`: `target/`, `.env`, `*.iml`, `.idea/`, `.vscode/`, `*.log`
- Commit `.env.example`, never `.env`.
- Never commit secrets or real personal data (use fake seed data).
- Optional: a `CHANGELOG.md` generated from tags, or just rely on the tag list.

---

## 17. Definition of done (per feature)

- [ ] Schema change committed (if any) and `db/init` updated
- [ ] Records, repository, service, view implemented following the layer rules
- [ ] Business rules covered by tests, each referencing its `BR-xx`
- [ ] No SQL outside repositories; no DB calls on the EDT
- [ ] Permission checks in the service layer
- [ ] Manually exercised through the UI with seed data
- [ ] Commits atomic with conventional messages; README/PRD updated if behavior changed
