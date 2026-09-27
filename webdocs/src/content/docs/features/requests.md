---
title: Service requests
description: The central entity — creation, lifecycle actions and the whole-story detail view.
---

Package `mx.marjan.requests`. A service request is the client's order; almost everything else hangs
off it.

## Actions and procedures

| Action | Procedure | Permission | Rule |
| --- | --- | --- | --- |
| Create | `sp_request_create` | `requests.write` | folio auto (BR-01) |
| Edit data | `sp_request_update` | `requests.write` | never changes status |
| Authorize | `sp_authorize_request` | `requests.write` | BR-03 / BR-04 (rate snapshot) |
| Schedule | `sp_schedule_request` | `requests.write` | BR-03 (delivery > pickup) |
| Cancel | `sp_cancel_request` | `requests.write` | BR-03 (reason required) |
| Close | `sp_close_request` (via `TripService`) | `requests.write` | BR-13 |
| Delete | `sp_request_delete` | `requests.write` | BR-14 cascade |
| Assign trip | `sp_assign_trip` | `trips.assign` | see [Assignment](/domain/assignment/) |

:::note
Closing lives in `TripService.closeRequest`, not `ServiceRequestService`, because it depends on the
trip's delivery. It still checks `requests.write`.
:::

## Creation

`sp_request_create` validates client/route/weight/dates (both dates or none, delivery after pickup),
then, inside a transaction:

1. allocates the id with `sp_next_id` (locking the sequence to serialize folios),
2. derives the year from the pickup date,
3. generates the folio with `fn_next_folio`,
4. inserts with status `requested` and `created_by`/`updated_by`.

`ServiceRequestService.create` re-reads the created request and returns it.

## The detail dialog — FR-REQ-5

`ServiceRequestDetailDialog.show(...)` builds a read-only "whole story" asynchronously: the request,
then its trip, costs (expenses total, advance balance), delivery and invoice. It composes
`TripService`, `ExpenseService`, `AdvanceService`, `DeliveryService` and `InvoiceService`.

## The UI

`ServiceRequestsView` has two toolbar rows:

- **Filters:** Folio, Cliente, Estado, Desde, Hasta · Buscar / Limpiar / Recargar.
- **Actions:** Nueva, Editar, Autorizar, Programar, Asignar viaje, Cancelar, Cerrar, Detalle,
  Eliminar.

Double-click opens the detail. Enter in the folio field searches. A 60-second timer (and every
reload) calls `sp_sweep_lifecycle` so time-driven transitions happen while the screen is open
(BR-03 automation).

## Java map

| Layer | Class |
| --- | --- |
| Record | `ServiceRequest`, `RequestFilter` |
| Enum | `RequestStatus` |
| Repository | `ServiceRequestRepository` |
| Service | `ServiceRequestService` (lifecycle), `TripService.closeRequest` |
| View | `ServiceRequestsView`, `ServiceRequestDetailDialog` |

## Gotchas

- `agreed_rate` is a **snapshot**: editing the client's rates later never changes an authorized
  request (BR-04).
- A request has at most one trip and one invoice (D4); both are `UNIQUE` on `service_request_id`.
- The cascade delete removes the trip and its costs, the delivery, the invoice and its payments —
  but **keeps audit rows** (BR-14/BR-22).

Next: [Trips](/features/trips/).
