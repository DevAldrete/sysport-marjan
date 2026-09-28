---
title: Stored procedures
description: The 112 procedures that implement every use case, grouped by domain.
---

Procedures are the app's **use cases**. A repository method maps to exactly one procedure. Most
write procedures follow the same shape:

1. set `p_problems = NULL`,
2. validate (collecting messages with `CONCAT_WS('; ', …)`),
3. `IF p_problems IS NOT NULL THEN LEAVE p;`,
4. do the work — inside a transaction when it touches several rows.

Procedures that mutate several rows declare
`DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; SET p_problems = '…'; END;`.

:::note
`OUT p_problems` is **NULL on success** and the rejection message otherwise. `INOUT p_problems`
helpers (`validate_*_into`) append their messages to a caller's string so one call can report all
problems at once.
:::

## Security — `10-security.sql`

| Procedure | Purpose |
| --- | --- |
| `sp_user_by_username`, `sp_user_by_id`, `sp_users_list`, `sp_roles_list`, `sp_role_permissions` | Reads for login and admin. |
| `sp_user_insert` (OUT id), `sp_user_update`, `sp_user_update_password`, `sp_user_delete` | User administration. Delete refuses own account / last admin (**BR-27**). |
| `sp_audit_log` | Appends to `audit_log`. |

## Clients, rates & routes — `20-clients.sql`

| Procedure | Purpose / rule |
| --- | --- |
| `sp_clients_search`, `sp_clients_active`, `sp_client_by_id` | Reads from `v_client`. |
| `sp_client_save` | Validate name/RFC (**BR-02**)/email/phone, credit data; insert or update. |
| `sp_client_delete` | Blocked when the client has requests, rates or invoices. |
| `sp_client_set_status` | Only `active` / `inactive`. |
| `sp_client_rates_by_client`, `sp_client_rate_suggest` | Rate reads; `suggest` = newest rate valid on a date (**BR-04**). |
| `sp_client_rate_save` | Validate route/rate/dates; **reject overlapping rate** for the same client+route+period (**BR-04**). |
| `sp_client_rate_delete` | Delete a rate. |
| `sp_routes_search`, `sp_route_by_id`, `sp_route_save`, `sp_route_delete` | Route CRUD; delete blocked when referenced. |
| `sp_route_stops`, `sp_route_stop_save`, `sp_route_stop_delete`, `sp_route_duplicate` | Ordered stops of a route (**BR-26**); duplicate path rejected, a visited stop cannot be removed. |

## Fleet — `30-fleet.sql`

| Procedure | Purpose / rule |
| --- | --- |
| `sp_vehicles_search`, `sp_vehicle_by_id` | Reads from `v_vehicle`. |
| `sp_vehicle_save` | Validate code/plates/capacity/mileage/year/plates-format; new → `available`; edit applies **BR-21** `mileage = GREATEST(mileage, new)`. |
| `sp_vehicle_set_status` | Manual statuses only; blocked while the vehicle has an active trip. |
| `sp_vehicle_delete` | Blocked when trips/fuel/maintenance exist. |
| `sp_eligible_vehicles_full(start,end)` | Available vehicles with **no overlapping active trip** (**FR-TRP-1**). |
| `sp_validate_fuel_load` | **BR-18** — liters/price/amount valid, amount ≈ liters×price, odometer ≥ current mileage, trip's vehicle matches. |
| `sp_fuel_save`, `sp_fuel_delete`, `sp_fuel_by_vehicle`, `sp_fuel_by_trip`, `sp_fuel_list`, `sp_fuel_sum_by_trip` | Fuel CRUD; `save` advances vehicle mileage in a transaction. |
| `sp_maintenance_by_vehicle`, `sp_maintenance_save`, `sp_maintenance_delete` | Maintenance CRUD; `save` advances mileage (**BR-21**) in a transaction. |

## Operators — `40-operators.sql`

| Procedure | Purpose / rule |
| --- | --- |
| `sp_employees_search`, `sp_employee_by_id` | Reads from `v_employee` (joins the license). |
| `sp_eligible_operators_full(start,end)` | Available operators with a valid license through `end` and no overlapping trip (**FR-TRP-1**). |
| `sp_validate_operator_assignment` (+ `validate_operator_assignment_into`) | **BR-06 / BR-09 / BR-10** — exists, assignable, licensed through the window, free. |
| `sp_employee_save` | Validate personal/emergency/license data; writes license **and** employee in one transaction. Blank number → `fn_next_license_number` (**BR-25**). |
| `sp_employee_delete` | Blocked when trips/advances/user exist; deletes employee + license in a transaction. |
| `sp_employee_set_status` | Manual statuses only; blocked while on an active trip. |

## Service requests — `50-requests.sql`

| Procedure | Purpose / rule |
| --- | --- |
| `sp_requests_search`, `sp_request_by_id`, `sp_requests_by_status`, `sp_requests_pending_billing` | Reads from `v_service_request`; `pending_billing` = rate>0, **`delivered`/`closed`**, no invoice (**FR-INV-1**). |
| `sp_request_create` | Validate client/route/weight/dates (both-or-none, delivery>pickup); allocate id, generate folio (**BR-01**), status `requested`, `created_by`/`updated_by`. |
| `sp_request_update` | Edit data only, never status; sets `updated_by`. |
| `sp_request_packages` | Read a request's package lines ordered by `line_no`. |
| `sp_package_save` (OUT id) | Upsert one package line (validation only; no transaction of its own — the repository composes several calls in one `Database.inTransaction`). |
| `sp_package_delete` | Delete one package line (used to remove lines absent from the submitted set); blocked after departure (**BR-27**). |
| `sp_package_receipt_save` | Per-unit tracking: set `received_quantity` (≤ declared) and `receipt_condition`. |
| `sp_request_delete` | **BR-14** cascade: request_packages, payments, invoices, expenses, advances, incidents, deliveries, trips, then the request. Audit rows are kept. |
| `sp_authorize_request` | **BR-03 / BR-04** — from `requested` only; rate>0; snapshots `agreed_rate`; audit `authorized`. |
| `sp_schedule_request` | **BR-03** — schedule or **reschedule** while `fn_request_reschedulable` (authorized/scheduled/assigned); both dates required, delivery > pickup; keeps the linked trip's planned window in sync. |
| `sp_cancel_request` | **BR-03** — pre-transit only; reason required (stored in `notes`). |
| `sp_close_request` | **FR-DEL-2 / BR-13** — only `delivered`; if `requires_documents`, the delivery must be `complete` with `received_by` + `evidence_reference`. |
| `sp_validate_vehicle_assignment` (+ `validate_vehicle_assignment_into`) | **BR-05…BR-11** — request ready to assign (`scheduled`, or `assigned` with a scheduled trip), vehicle assignable, capacity ≥ effective weight, no overlap. |

## Trips, deliveries & incidents — `60-trips.sql`

| Procedure | Purpose / rule |
| --- | --- |
| `sp_trips_search`, `sp_trip_by_id`, `sp_trip_by_request`, `sp_trip_vehicle` | Reads from `v_trip`; `sp_trip_vehicle` returns the vehicle of a trip (fuel loads). |
| `sp_assign_trip` | **FR-TRP-2 / BR-05…BR-11** — transaction with `FOR UPDATE` locks on request/vehicle/operator, runs both validators, inserts the trip (`scheduled`, `estimated_km` from the route), sets the request `assigned`, audit `assigned`. |
| `sp_depart_trip` | Trip `scheduled → in_transit`; request → `in_transit`; vehicle & employee → `on_trip`; sets departure. |
| `sp_arrive_trip` | Trip `in_transit → completed`; vehicle `available` + **BR-21** `mileage += actual_km`; employee `available`; if the request does not require documents → `delivered`. |
| `sp_cancel_trip` | Only `scheduled`; reason required; cancels trip + request; audit `cancelled`. |
| `sp_reassign_trip` | **BR-15** — only a `scheduled` trip whose request is `assigned`; re-validates both resources; audit `reassigned`. |
| `sp_trip_delete` | **BR-27** — only `scheduled`/`cancelled`; cascades children; reverts the request to `scheduled` when needed. |
| `sp_trip_stops`, `sp_trip_stop_arrival_save`, `sp_trip_stop_arrival_delete` | Planned stops with actual arrivals (**BR-26**). |
| `sp_sweep_lifecycle` (OUT changes) | Batch: promotes `authorized` requests with dates to `scheduled`, and departs scheduled trips whose `planned_start ≤ NOW()`. Called from the UI timer and on reload (**BR-03** automation). |
| `sp_delivery_by_trip`, `sp_delivery_save`, `sp_delivery_delete` | **BR-12 / BR-13** — one delivery per trip; the trip must be `completed`; a delivery moves the request to `delivered`. |
| `sp_incidents_by_trip`, `sp_incident_save`, `sp_incident_delete` | Incident log per trip. |

## Costs & advances — `70-costs.sql`

| Procedure | Purpose / rule |
| --- | --- |
| `sp_expenses_by_trip`, `sp_expense_save`, `sp_expense_delete` | Expenses; `save` validates type + amount>0 (**BR-17**). |
| `sp_validate_expense`, `sp_validate_advance` | Field validation helpers. |
| `sp_advances_by_trip`, `sp_advance_save`, `sp_advance_delete` | Advances; `save` inserts `pending`. |
| `sp_settle_advance` | **BR-16** — mark `settled` + `settled_at` + `settled_by` only from `pending`. |
| `sp_advance_balance` (OUT given, proven, balance) | **BR-16** — `proven = Σ expenses + Σ fuel`; balance via `fn_advance_balance`. |

## Finance — `80-finance.sql`

| Procedure | Purpose / rule |
| --- | --- |
| `sp_invoices_search`, `sp_invoice_by_id`, `sp_invoice_by_request`, `sp_payments_by_invoice` | Reads from `v_invoice`. |
| `sp_create_invoice_from_request` | **BR-20** — only `delivered`/`closed`, rate>0, no existing invoice; due date via `fn_invoice_due_date`; generates the invoice number. |
| `sp_register_payment` | **BR-19** — lock invoice, block cancelled, amount>0, block overpayment; insert; recompute status. |
| `sp_refresh_invoice_statuses` (OUT changed) | **BR-19 / FR-INV-3** — bulk recompute for non-cancelled invoices. |
| `sp_cancel_invoice` | **BR-19** — cannot cancel a paid, already-cancelled, or paid-into invoice. |
| `sp_invoice_delete`, `sp_payment_delete` | Invoice delete only for `pending` with no payments; payment delete re-derives the invoice status (**BR-27** / **BR-19**). |

## Reports & dashboard — `90-reports.sql`

| Procedure | Report / purpose |
| --- | --- |
| `sp_revenue_by_client(from,to)` | Revenue by client (**FR-RPT-1**). |
| `sp_route_usage(from,to)` | Most-used routes (**FR-RPT-2**). |
| `sp_vehicle_usage(from,to)` | Trips & km per vehicle (**FR-RPT-3**). |
| `sp_fuel_efficiency(from,to)` | km per liter per vehicle. |
| `sp_profitability(from,to)` | `agreed_rate − expenses − fuel` per trip (**FR-RPT-4**). |
| `sp_receivables()` | Outstanding balances (**FR-RPT-6**). |
| `sp_expiring_licenses(today)` | Licenses expiring within 30 days (**BR-10**). |
| `sp_maintenance_due(today)` | Vehicles whose next service date/km is due. |
| `sp_dashboard(today, OUT×4)` | Counters: expiring licenses, overdue invoices, maintenance due, requests awaiting assignment (**FR-DSH-1**). |

## The assignment transaction (read this one)

`sp_assign_trip` is the heart of the system:

```sql
START TRANSACTION;
  SELECT ... FROM service_requests WHERE id = p_request_id FOR UPDATE;
  SELECT ... FROM vehicles        WHERE id = p_vehicle_id FOR UPDATE;
  SELECT ... FROM employees       WHERE id = p_operator_id FOR UPDATE;
  CALL validate_vehicle_assignment_into(p_request_id, p_vehicle_id, p_problems);
  CALL validate_operator_assignment_into(p_request_id, p_operator_id, p_problems);
  IF p_problems IS NOT NULL THEN ROLLBACK; LEAVE p; END IF;
  -- insert trip, set request 'assigned', write audit row
COMMIT;
```

The `FOR UPDATE` locks prevent two dispatchers from assigning the same resource at the same time;
the overlap check then sees a consistent view. See [Assignment](/domain/assignment/).

Next: [Views](/database/views/).
