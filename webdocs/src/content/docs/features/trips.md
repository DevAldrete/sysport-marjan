---
title: Trips
description: Assignment, departure/arrival, reassignment and the trip detail workspace.
---

Package `mx.marjan.trips`. A trip is the physical execution of a request. It is created at
assignment and owns costs, incidents and the delivery.

## Actions and procedures

| Action | Procedure | Permission | Rule |
| --- | --- | --- | --- |
| Assign | `sp_assign_trip` | `trips.assign` | BR-05…BR-11 |
| Depart | `sp_depart_trip` | `trips.write` | trip → in_transit |
| Arrive | `sp_arrive_trip` | `trips.write` | BR-21 mileage |
| Reassign | `sp_reassign_trip` | `trips.assign` | BR-15 (before departure) |
| Cancel | `sp_cancel_trip` | `trips.write` | only `scheduled` |
| Delete | `sp_trip_delete` | `trips.write` | BR-27 (only `scheduled`/`cancelled`) |
| Sweep | `sp_sweep_lifecycle` | `trips.write` | time-driven transitions |
| Register delivery | `sp_delivery_save` | `deliveries.write` | BR-12 / BR-13 |
| Register incident | `sp_incident_save` | `incidents.write` | — |

See [Assignment](/domain/assignment/) for the assignment transaction in detail.

## Trip detail — the workspace

`TripDetailDialog` is a modal with tabs:

| Tab | Contents |
| --- | --- |
| **Resumen** | expenses total, fuel total, advance settlement |
| **Paradas** | the route's ordered stops + actual arrival (BR-26): Marcar llegada / Quitar llegada |
| **Gastos** | expenses table + Nuevo / Eliminar |
| **Combustible** | fuel loads for the trip |
| **Anticipos** | advances table + Registrar / Comprobar (settle) / Eliminar |
| **Incidencias** | incidents table + Nueva / Eliminar |
| **Paquetes** | the request's packages + Registrar recepcion (received quantity and condition per line) / Recibir todo / Recargar |
| **Entrega** | delivery status + Registrar / actualizar (status `complete` or `pending_documents`) |

The **Resumen** total is computed from `ExpenseService` + `FuelService`; the advance outcome comes
from `AdvanceService.balanceForTrip` (BR-16).

## Stops (BR-26)

The trip inherits its route's ordered stops. The **Paradas** tab lists them (`sp_trip_stops`) and
lets the user record the actual arrival at each one (`sp_trip_stop_arrival_save`), so the history
shows the truck went through every intermediate point and not just from origin to destination. A
stop already visited cannot be removed from the route.

## Packages & per-unit tracking

The trip inherits the request's package list (`request_packages`). The **Paquetes** tab lists each
line and lets the user record what actually arrived: `received_quantity` (capped at the declared
quantity) and `receipt_condition` (`ok`, `shortage`, `damaged`, `missing`) via
`sp_package_receipt_save`. This is the per-unit tracking that a plain cargo description cannot give;
a shortage here is the natural trigger for a `cargo_damage` incident.

## Deliveries — BR-12 / BR-13

One delivery per trip (`deliveries.trip_id UNIQUE`). `sp_delivery_save` requires the trip to be
`completed`, validates the fields and status, and moves the request to `delivered`. A delivery is
`complete` when it has `received_by` and `evidence_reference`.

## Incidents

Free-form operational log per trip: type (`accident`, `mechanical_failure`, `delay`, `road_closure`,
`cargo_damage`, `documentation_issue`, `other`), date/time, location, description, actions taken.

## The UI

`TripsView`: filter (Buscar + Estado) and actions Salida, Llegada, Reasignar, Cancelar, Detalle,
Eliminar. Double-click opens the detail. `reload()` runs `sweepLifecycle()` first. Actions are
enabled per permission (`trips.write` / `trips.assign`) **and** the selected trip's status: Salida
and Reasignar/Cancelar only when `scheduled`, Llegada only when `in_transit`, Eliminar when
`scheduled`/`cancelled`.

## Java map

| Layer | Class |
| --- | --- |
| Records | `Trip`, `Delivery`, `Incident` |
| Enums | `TripStatus`, `DeliveryStatus`, `IncidentType` |
| Repositories | `TripRepository`, `DeliveryRepository`, `IncidentRepository` |
| Services | `TripService`, `DeliveryService`, `IncidentService` |
| Views | `TripsView`, `TripDetailDialog` |

Next: [Fleet](/features/fleet/).
