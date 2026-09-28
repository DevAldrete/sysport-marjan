---
title: Lifecycles
description: The state machines for requests, trips, vehicles, operators, deliveries and invoices.
---

Every status is an English code stored in the database and mirrored as a Java enum. **Transitions
live in the database**, not in the enums.

## Service request — `service_requests.status`

```text
requested ─► authorized ─► scheduled ─► assigned ─► in_transit ─► delivered ─► closed
    │            │             │            │
    └────────────┴─────────────┴────────────┴──► cancelled   (any time before in_transit)
```

| Transition | Trigger | Condition | Enforced in |
| --- | --- | --- | --- |
| requested → authorized | authorize | `agreed_rate > 0` | `sp_authorize_request` + `fn_request_can_transition` |
| authorized → scheduled | schedule | both dates present, delivery > pickup | `sp_schedule_request` |
| scheduled/assigned → same | reschedule | not yet `in_transit` (`fn_request_reschedulable`); keeps status, syncs the trip window | `sp_schedule_request` |
| scheduled → assigned | assign trip | passes BR-05…BR-11 | `sp_assign_trip` |
| assigned → in_transit | depart | — | `sp_depart_trip` |
| in_transit → delivered | delivery recorded | delivery exists (or no documents required → arrival) | `sp_delivery_save` / `sp_arrive_trip` |
| delivered → closed | close | documentation complete (BR-13) | `sp_close_request` |
| any pre-transit → cancelled | cancel | reason required | `sp_cancel_request` / `sp_cancel_trip` |

`fn_request_can_transition(p_from, p_to)` returns 1/0 and is the single place the graph is encoded.
Java's `RequestStatus` is only the typed vocabulary.

## Trip — `trips.status`

```text
scheduled ─► in_transit ─► completed
    │
    └──► cancelled
```

- Created at assignment (`scheduled`).
- `depart` sets `in_transit` and flips vehicle + operator to `on_trip`.
- `arrive` sets `completed`, frees the resources, and adds `actual_km` to the vehicle's mileage.
- `cancel` is only allowed from `scheduled`.

`TripStatus.isActive()` (in Java) is `SCHEDULED || IN_TRANSIT` — the window used for overlap checks.

## Vehicle — `vehicles.status`

```text
available ⇄ assigned ⇄ on_trip
     ↕
maintenance / out_of_service      (manual, maintenance role)
decommissioned                    (terminal)
```

`assigned` and `on_trip` are owned by the trip lifecycle; only `available`, `maintenance`,
`out_of_service`, `decommissioned` can be set manually (`VehicleStatus.manualValues()`).

## Operator — `employees.status`

```text
available ⇄ on_trip
     ↕
resting / vacation / incapacitated   (manual)
terminated                           (terminal)
```

Manual values: `available`, `resting`, `vacation`, `incapacitated`, `terminated`
(`EmployeeStatus.manualValues()`). `on_trip` is owned by the trip lifecycle.

## Delivery — `deliveries.status`

```text
pending_documents ─► complete
```

A delivery is `complete` when it has `received_by` and `evidence_reference`. BR-13 requires a
complete delivery before a documents-required request can close.

## Invoice — `invoices.status`

```text
pending ─► paid
   │  └──► overdue        (due_date < today and unpaid)
   └─────► cancelled
```

Status is **derived** by `fn_invoice_status` from the sum of payments and the due date, so it can
never drift. `sp_refresh_invoice_statuses` recomputes it in bulk; `sp_register_payment` recomputes
it on each payment.

## Availability is not just a status

:::tip
The stored status covers **manual** conditions (maintenance, vacation…). Whether someone is free
for a given date range is decided by **checking overlapping trips**, not by trusting a flag — a
flag goes stale. This is BR-05/BR-06 and decision D5.
:::

## The lifecycle sweep

Some transitions are **time-driven**. `sp_sweep_lifecycle`:

1. promotes `authorized` requests that have valid dates to `scheduled`;
2. departs `scheduled` trips whose `planned_start ≤ NOW()`.

It runs from `ServiceRequestsView`/`TripsView` on reload and from a 60-second timer. It returns the
number of changes so the UI can refresh only when something moved.

Next: [Business rules](/domain/business-rules/).
