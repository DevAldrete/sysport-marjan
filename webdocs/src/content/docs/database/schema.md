---
title: Schema
description: All 25 tables, grouped by domain, with their key columns and constraints.
---

The schema is defined in **`db/init/01-tables.sql`**. MySQL 8.4, InnoDB, `utf8mb4` /
`utf8mb4_0900_ai_ci`.

## Conventions

- **No `AUTO_INCREMENT`.** Every `id` is `BIGINT PRIMARY KEY`, allocated by `sp_next_id`
  (see [Database access](/architecture/database-access/#id-allocation-no-auto_increment)).
- **English status codes**, mirrored as Java enums + `CHECK` constraints.
- **Money is `DECIMAL(12,2)`.** Quantities: liters `DECIMAL(8,2)`, price/liter `DECIMAL(8,3)`,
  km/weight/odometer `DECIMAL(10,1)`. Never `DECIMAL` without precision (cents vanish).
- **FK direction:** the **child** column references the **parent** `id`. 1:1 links keep a `UNIQUE`
  on the child column.
- **`created_by` / `updated_by`** are written explicitly by procedures (there are **no triggers**).
- **Snapshot principle:** history never changes when current data changes. `agreed_rate` is copied
  at authorization; a trip stores its vehicle/operator ids permanently.

## Entity overview

```text
roles ─< role_permissions >─ permissions
roles ─< users >─ employees ─ licenses
clients ─< client_rates >─ routes ─< route_stops
clients ─< service_requests >─ routes
service_requests ─< request_packages
service_requests ─1:1─ trips >─ vehicles
                       trips >─ employees
                       trips ─< trip_stop_arrivals >─ route_stops
trips ─< expenses        trips ─< advances
trips ─< incidents       trips ─1:1─ deliveries
vehicles ─< fuel_loads >─ trips (optional)
vehicles ─< maintenance
service_requests ─< invoices ─< payments
audit_log, sequences
```

## Security

| Table | Key columns |
| --- | --- |
| `roles` | `id` PK · `name` NOT NULL UNIQUE |
| `permissions` | `id` PK · `name` NOT NULL UNIQUE |
| `role_permissions` | PK(`role_id`,`permission_id`) · FKs → `roles`, `permissions` |

## People

| Table | Key columns / constraints |
| --- | --- |
| `licenses` | `license_number` NOT NULL UNIQUE · `license_type` NOT NULL CHECK(`Federal A..E`,`Estatal`,`Otro`) · `expiration_date` NOT NULL · index on `expiration_date` |
| `employees` | `name` NOT NULL · `phone` NOT NULL UNIQUE · `email`/`rfc`/`curp` UNIQUE · `license_id` UNIQUE FK→`licenses` · `status` CHECK(`available,on_trip,resting,vacation,incapacitated,terminated`) |
| `users` | `employee_id` UNIQUE FK→`employees` · `username` NOT NULL UNIQUE · `password_hash` NOT NULL · `role_id` FK→`roles` · `status` CHECK(`active,disabled`) |

## Fleet

| Table | Key columns / constraints |
| --- | --- |
| `vehicles` | `internal_code` NOT NULL UNIQUE · `plates` NOT NULL UNIQUE · `serial_number` UNIQUE · `year` CHECK 1950–2100 · `load_capacity` ≥0 · `mileage` NOT NULL ≥0 · `status` CHECK(`available,assigned,on_trip,maintenance,out_of_service,decommissioned`) |
| `maintenance` | `vehicle_id` FK · `maintenance_date` NOT NULL · `maintenance_type` CHECK(`preventive`,`corrective`) · `cost` ≥0 · `next_service_date`, `next_service_km` |
| `fuel_loads` | `vehicle_id` FK · `trip_id` FK (nullable) · `liters` >0 · `price_per_liter` >0 · `amount` >0 · `odometer_reading` ≥0 |

## Clients & routes

| Table | Key columns / constraints |
| --- | --- |
| `clients` | `name` NOT NULL · `rfc` NOT NULL UNIQUE · `client_type` CHECK(`occasional`,`frequent`) · `payment_terms` CHECK(`cash`,`credit`) · `credit_limit` ≥0 · `credit_days` ≥0 · `status` CHECK(`active`,`inactive`) |
| `routes` | `origin`,`destination` NOT NULL (first/last stop snapshots) · `estimated_km` ≥0 · `description` |
| `route_stops` | `route_id` FK · `sequence_no` · `location` NOT NULL · index `(route_id)` — **BR-26**, the ordered path |
| `client_rates` | `client_id` FK · `route_id` FK · `rate` >0 · `valid_from` NOT NULL · `valid_to` · index `(client_id,route_id,valid_from)` |

## Requests, trips & operations

| Table | Key columns / constraints |
| --- | --- |
| `service_requests` | `folio` NOT NULL UNIQUE · `client_id`,`route_id` NOT NULL FK · `cargo_description` · `estimated_weight` ≥0 (manual **fallback**) · `agreed_rate` >0 · `requires_documents` NOT NULL DEFAULT TRUE · `status` CHECK(`requested,authorized,scheduled,assigned,in_transit,delivered,closed,cancelled`) · `ck_sr_dates` (delivery > pickup) · `created_by`,`updated_by` FK→`users` |
| `request_packages` | `service_request_id` FK · `line_no` · `description` NOT NULL · `quantity` >0 · `unit` CHECK(`caja,paleta,saco,bulto,pieza,contenedor,otro`) · `unit_weight` ≥0 (line weight = quantity × unit_weight) · `received_quantity` ≥0 + `receipt_condition` CHECK(`ok,shortage,damaged,missing`) filled at delivery |
| `trips` | `service_request_id` NOT NULL **UNIQUE** FK (1:1) · `vehicle_id`,`employee_id` NOT NULL FK · `planned_start`/`planned_end` NOT NULL · `ck_trip_window` (end > start) · `status` CHECK(`scheduled,in_transit,completed,cancelled`) |
| `expenses` | `trip_id` FK · `expense_type` CHECK(8 values) · `amount` >0 · `expense_date` NOT NULL |
| `advances` | `trip_id`,`employee_id` FK · `amount_given` >0 · `delivered_date` NOT NULL · `status` CHECK(`pending`,`settled`) · `settled_at`,`settled_by` |
| `incidents` | `trip_id` FK · `incident_type` CHECK(7 values) · `incident_date` NOT NULL |
| `deliveries` | `trip_id` NOT NULL **UNIQUE** FK (1:1, BR-12) · `status` CHECK(`pending_documents`,`complete`) · `received_by`, `evidence_reference` |
| `trip_stop_arrivals` | `trip_id` FK · `route_stop_id` FK · UNIQUE(`trip_id`,`route_stop_id`) · `arrived_at`, `notes` — **BR-26**, the actual stop visits |

## Finance

| Table | Key columns / constraints |
| --- | --- |
| `invoices` | `service_request_id` NOT NULL **UNIQUE** FK (BR-20) · `invoice_number` NOT NULL UNIQUE · `amount` >0 · `issue_date`,`due_date` NOT NULL · `ck_invoice_dates` (due ≥ issue) · `status` CHECK(`pending`,`paid`,`overdue`,`cancelled`) · index `(status,due_date)` |
| `payments` | `invoice_id` FK · `amount` >0 · `payment_date` NOT NULL · `payment_method` CHECK(`cash`,`transfer`,`check`,`card`,`other`) |

## Audit & sequences

| Table | Purpose |
| --- | --- |
| `audit_log` | `user_id` FK · `entity` · `entity_id` · `action` · `details` · index `(entity,entity_id)`. Written by `sp_audit_log` and by key lifecycle procedures. |
| `sequences` | `name` PK · `next_value` NOT NULL. The single id allocator. |

## Indexes that matter

- `trips(vehicle_id, planned_start)` and `trips(employee_id, planned_start)` — the overlap checks.
- `trips(planned_start)`, `service_requests(pickup_date_scheduled)` — date filters.
- `service_requests(status)`, `invoices(status, due_date)` — list filters and overdue detection.
- `fuel_loads(vehicle_id, load_date)`, `fuel_loads(load_date)` — fuel reports.
- `employees(status)`, `vehicles(status)` — eligibility.

Next: [Functions](/database/functions/).
