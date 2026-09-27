---
title: Assignment
description: The heart of the system — how a trip gets a vehicle and an operator, safely.
---

Assignment is the highest-risk operation: two dispatchers must never be able to book the same
vehicle or operator. It is also where the most rules meet (BR-05…BR-11).

## Two-step design

```text
1. Eligibility (fast, for the dialog)    →  sp_eligible_vehicles_full / sp_eligible_operators_full
2. Validation (authoritative, on submit) →  sp_assign_trip  (inside a transaction)
```

The eligible lists make the dialog useful; they are **not** trusted. The submit re-validates
everything inside a locked transaction, so a stale list can never cause a double-booking.

## Step 1 — eligible resources

`sp_eligible_vehicles_full(start, end)` returns vehicles that are `available` **and** have no
overlapping active trip in `[start, end)`.

`sp_eligible_operators_full(start, end)` returns operators that are `available`, have a license
valid **through `end`**, and have no overlapping active trip.

The overlap predicate is:

```sql
status IN ('scheduled','in_transit')
AND planned_start < :end AND planned_end > :start
```

This is the standard half-open interval overlap test.

## Step 2 — the assignment transaction

`sp_assign_trip(request, vehicle, operator, user)` runs, in one transaction:

```sql
START TRANSACTION;
  SELECT ... FROM service_requests WHERE id = p_request_id FOR UPDATE;
  SELECT ... FROM vehicles        WHERE id = p_vehicle_id FOR UPDATE;
  SELECT ... FROM employees       WHERE id = p_operator_id FOR UPDATE;

  CALL validate_vehicle_assignment_into(p_request_id, p_vehicle_id, p_problems);
  CALL validate_operator_assignment_into(p_request_id, p_operator_id, p_problems);

  IF p_problems IS NOT NULL THEN ROLLBACK; LEAVE p; END IF;

  -- allocate id, INSERT trip (status 'scheduled'),
  -- UPDATE request status = 'assigned',
  -- INSERT audit row ('assigned')
COMMIT;
```

The `FOR UPDATE` locks serialize concurrent assignment of the same resource; the overlap check then
sees a consistent view. If any rule fails, the whole thing rolls back and **all** problems are
returned together (they accumulate into `p_problems`).

## What is validated

| Check | Rule | Function |
| --- | --- | --- |
| Request exists and is `scheduled` | — | `sp_validate_vehicle_assignment` |
| Vehicle assignable | BR-07 / BR-11 | `fn_vehicle_assignable` |
| Capacity ≥ weight | BR-08 | `fn_capacity_ok` |
| Vehicle free in the window | BR-05 | overlap query |
| Operator assignable | BR-09 | `fn_employee_assignable` |
| License valid through `planned_end` | BR-09 / BR-10 | `sp_validate_operator_assignment` |
| Operator free in the window | BR-06 | overlap query |

## In Java

```java
// TripService
public Result<Trip> assign(long requestId, long vehicleId, long operatorId) {
    if (!Session.has(Permissions.TRIPS_ASSIGN)) {
        return Result.err("No tiene permiso para asignar viajes");
    }
    Result<Long> saved = trips.assign(requestId, vehicleId, operatorId, Session.userId());
    // … re-read the trip and return it
}
```

`ServiceRequestsView.openAssign()` loads the eligible lists, shows a dialog, and calls
`TripService.assign(...)`. `TripRepository.assign` calls `sp_assign_trip` via
`Database.callForProblemsAndId` (the procedure's OUT order is `p_problems`, then `p_trip_id`).

## Reassignment — BR-15

`sp_reassign_trip` is allowed **only** while the trip is `scheduled` and the request is still
`scheduled` (before departure). It re-runs both validators and writes a `reassigned` audit row. After
`in_transit` the resources are frozen — changing history is not allowed.

## Departure & arrival

- `sp_depart_trip`: trip `scheduled → in_transit`, request → `in_transit`, vehicle & operator →
  `on_trip`, sets `departure_datetime`.
- `sp_arrive_trip`: trip `in_transit → completed`, vehicle → `available` and **BR-21** adds
  `actual_km` to `vehicles.mileage`, operator → `available`. If the request does not require
  documents, it becomes `delivered` immediately.

:::tip
Read `sp_assign_trip`, `sp_validate_vehicle_assignment` and `sp_validate_operator_assignment` in
`db/init/50-requests.sql` and `db/init/60-trips.sql`. Together they are the most important code in
the project.
:::

Next: [Costs & advances](/domain/costs/).
