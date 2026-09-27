---
title: Seed data & roles
description: The roles, the 33 permissions, and the demo data loaded by 99-seed.sql.
---

`db/init/99-seed.sql` runs last. It creates the role/permission matrix and a full set of fake demo
rows so every screen has something to show.

:::danger
All seed passwords are `admin123` (BCrypt-hashed). This is **development-only** data. Never reuse it.
:::

## Roles

| id | Role | Typical person |
| --- | --- | --- |
| 1 | `admin` | Owner — everything |
| 2 | `traffic` | Dispatcher |
| 3 | `maintenance` | Fleet manager |
| 4 | `collections` | Billing clerk |
| 5 | `viewer` | Read-only |

## Permissions (33)

`clients.read`, `clients.write`, `routes.read`, `routes.write`, `rates.read`, `rates.write`,
`operators.read`, `operators.write`, `fleet.read`, `fleet.write`, `fleet.maintenance`, `fuel.read`,
`fuel.write`, `requests.read`, `requests.write`, `requests.assign`, `trips.read`, `trips.write`,
`trips.assign`, `expenses.read`, `expenses.write`, `advances.read`, `advances.write`,
`deliveries.read`, `deliveries.write`, `incidents.read`, `incidents.write`, `invoices.read`,
`invoices.write`, `payments.read`, `payments.write`, `reports.view`, `security.users`.

The Java mirror is `mx.marjan.security.Permissions` — **keep the two in sync**.

## Permission matrix

| Role | Permissions |
| --- | --- |
| `admin` | all 33 |
| `traffic` | clients, routes, rates, operators.read, fleet.read, fuel, requests (+assign), trips (+assign), expenses, advances, deliveries, incidents, payments.read, reports.view |
| `maintenance` | fleet.read/write/maintenance, fuel.read/write, operators.read, reports.view |
| `collections` | invoices, payments, clients.read, requests.read, reports.view |
| `viewer` | every `*.read` permission + `reports.view` |

## Demo entities

| Entity | Count | Notes |
| --- | --- | --- |
| `licenses` | 10 | `LIC-MRJ-0001`…; one already expired, several near expiry |
| `employees` | 10 | mixed statuses: available, on_trip, vacation, resting |
| `users` | 5 | `admin`, `traffic`, `maint`, `collections`, `viewer` |
| `clients` | 10 | mixed occasional/frequent, cash/credit; one inactive |
| `routes` | 10 | CDMX ↔ Monterrey/Guadalajara/Mérida/…, 90–1300 km |
| `client_rates` | 10 | valid from 2026-01-01 |
| `vehicles` | 10 | `ECO-01`…; one maintenance, one out_of_service, one decommissioned |
| `service_requests` | 10 | `SR-2026-000001`… across all statuses |
| `trips` | 6 | completed, in_transit, scheduled |
| `deliveries` | 4 | complete, with evidence references |
| `expenses` | 10 | tolls/food/lodging/repairs |
| `advances` | 8 | 3 settled, 5 pending |
| `fuel_loads` | 10 | 6 tied to trips, 4 without |
| `maintenance` | 6 | preventive/corrective |
| `incidents` | 6 | mechanical failure, delay, road closure, cargo damage, other |
| `invoices` | 4 | one overdue, one paid, two pending |
| `payments` | 6 | cash/transfer/check/card |

## Ids used by integration tests

`SqlRulesTest` relies on specific seed ids (freshly seeded DB): **client 2, route 4, vehicles
5/8/10, operators 7/8/10, admin user 1**. If you change the seed, update the tests (or the ids they
use) in the same commit.

## Sequences are primed

The seed ends by inserting `next_value = COALESCE(MAX(id),0)` for all 18 id-allocated tables, so the
first `sp_next_id` call returns `MAX(id)+1`. See
[Database access](/architecture/database-access/#id-allocation-no-auto_increment).

## Admin login

```text
usuario:  admin
password: admin123
```

Next: [Schema changes](/database/migrations/).
