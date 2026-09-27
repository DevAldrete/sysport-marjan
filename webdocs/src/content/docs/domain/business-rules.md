---
title: Business rules
description: BR-01 to BR-24 — the rules, and exactly where each one is enforced.
---

Every rule has an ID so code, tests and commits can reference it. **Search the codebase for `BR-xx`
to find both the rule and the code that enforces it.** Most rules are enforced by a stored procedure
or function; the table below points at the real implementation.

| ID | Rule | Enforced in |
| --- | --- | --- |
| **BR-01** | Every service request gets a unique folio (`SR-2026-000123`). | `fn_next_folio` + `sp_request_create`; `service_requests.folio UNIQUE` |
| **BR-02** | Client RFC is unique and format-validated. | `fn_rfc_valid` + `sp_client_save`; `clients.rfc UNIQUE` |
| **BR-03** | Service requests follow the state machine; invalid transitions are rejected. | `fn_request_can_transition`; `sp_authorize/schedule/cancel/close_request` |
| **BR-04** | `agreed_rate` is a snapshot set at authorization, never recalculated; suggested from `client_rates` valid on the date. Overlapping rates for the same client+route are rejected. | `sp_client_rate_suggest`, `sp_authorize_request`, `sp_client_rate_save` |
| **BR-05** | A vehicle cannot be in two trips with overlapping planned windows. | `sp_validate_vehicle_assignment` inside `sp_assign_trip` (with `FOR UPDATE`) |
| **BR-06** | An operator cannot be in two trips with overlapping planned windows. | `sp_validate_operator_assignment` inside `sp_assign_trip` |
| **BR-07** | A vehicle must be assignable (not maintenance/out_of_service/decommissioned). | `fn_vehicle_assignable` |
| **BR-08** | Vehicle capacity ≥ request's estimated weight. | `fn_capacity_ok` |
| **BR-09** | Operator must be assignable and have a license valid through the planned end. | `fn_employee_assignable` + `sp_validate_operator_assignment` |
| **BR-10** | Licenses expiring within 30 days warn; expired ones block assignment. | `sp_validate_operator_assignment` (block); `sp_expiring_licenses` + `sp_dashboard` (warn) |
| **BR-11** | An `out_of_service` vehicle cannot be assigned until returned to `available`. | `fn_vehicle_assignable` + `sp_vehicle_set_status` |
| **BR-12** | Each trip has at most one delivery. | `deliveries.trip_id UNIQUE` |
| **BR-13** | A request requiring documents cannot close until its delivery is `complete`. | `sp_close_request` |
| **BR-14** | History is kept via status; an explicit confirmed hard delete is also available, but the database refuses to delete a parent with related rows. | `sp_*_delete` (cascade/guard), services, `Ui.delete` |
| **BR-15** | Reassigning a trip's vehicle/operator is allowed only before `in_transit`, is validated like a new assignment, and is audited. | `sp_reassign_trip` |
| **BR-16** | Advance balance = `given − (expenses + fuel)`; positive → operator returns, negative → company reimburses, zero → settled. | `fn_advance_balance`, `sp_advance_balance`, `sp_settle_advance` |
| **BR-17** | Expense type must be allowed and amount > 0. | `expenses` CHECK + `sp_validate_expense` |
| **BR-18** | Fuel: `amount ≈ liters × price` (±0.05); odometer never decreases; the trip's vehicle matches the load's. | `fn_amount_matches` + `sp_validate_fuel_load` |
| **BR-19** | Invoice `paid` when Σ payments ≥ amount; `overdue` when past due and unpaid; payments cannot exceed the balance. Cash → due = issue; credit → due = issue + credit_days. | `fn_invoice_status`, `fn_invoice_due_date`, `sp_register_payment`, `sp_cancel_invoice`, `sp_refresh_invoice_statuses` |
| **BR-20** | One invoice per service request; only for `delivered`/`closed` requests. | `invoices.service_request_id UNIQUE` + `sp_create_invoice_from_request` |
| **BR-21** | Maintenance/fuel/trip-completion updates `vehicles.mileage` when the reading is higher. | `sp_arrive_trip`, `sp_vehicle_save`, `sp_maintenance_save`, `sp_fuel_save` |
| **BR-22** | Key operations store `created_by`/`updated_by`; important actions write an audit row. | procedures + `sp_audit_log` |
| **BR-23** | Users need a permission per operation; disabled users cannot log in. | service checks + `sp_role_permissions` + `AuthService.login` |
| **BR-24** | Passwords are stored only as BCrypt hashes, never logged. | `AuthService` |

## How rules are enforced

Three layers, each doing what it is best at:

| Layer | Enforces | Examples |
| --- | --- | --- |
| **Database constraints** | things that must never be false | `UNIQUE`, `NOT NULL`, `CHECK`, `FK` |
| **Stored procedures / functions** | business decisions | state machine, overlap, payments, close |
| **Service permission checks** | who may act | `Session.has(...)` per use case |

The UI never *decides* a rule; it only avoids offering impossible actions (e.g. status pickers list
manual values only).

## Functional requirement IDs

The code also references `FR-*` ids from the PRD (e.g. `FR-TRP-1` eligible resources, `FR-INV-1`
pending billing, `FR-RPT-1..7` reports, `FR-DSH-1` dashboard). These map to features rather than
rules; grep the same way.

## Adding a rule

1. Put the decision in a `fn_*` function or a `sp_*` procedure and give it a new `BR-xx`.
2. Reference it in the code comment (`-- BR-25: …`).
3. Add a test that exercises the rule (see [Testing](/start/testing/)).
4. Add the row to this table and to `PRD.md`.
5. Commit the rule and its test together.

Next: [Assignment](/domain/assignment/).
