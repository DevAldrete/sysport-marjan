---
title: Clients & rates
description: Client records, negotiated per-route rates and route master data.
---

Package `mx.marjan.clients` (and `mx.marjan.routes`). These are the master-data foundations
everything else references.

## Clients

`Client` carries commercial data: type (occasional/frequent), payment terms (cash/credit), credit
limit and days, plus contact info.

| Operation | Service | Permission |
| --- | --- | --- |
| Search / list active / find | `ClientService` | none |
| Save | `ClientService.save` | `clients.write` |
| Activate / deactivate | `ClientService.activate/deactivate` | `clients.write` |
| Delete | `ClientService.delete` | `clients.write` |

Rules (**BR-02**): RFC is required, unique and format-validated by `fn_rfc_valid`; email and phone
are format-checked by `sp_client_save`. Credit days must be 0–3650 when terms are `credit`.

:::note
Deactivate instead of delete is the normal lifecycle (BR-14). A hard delete is allowed but the
database refuses when the client has requests, rates or invoices (`sp_client_delete`).
:::

## Rates — BR-04

`client_rates` are negotiated prices per client + route, with a validity window.

| Operation | Procedure |
| --- | --- |
| List a client's rates | `sp_client_rates_by_client` |
| Suggest a rate for a date | `sp_client_rate_suggest` (newest rate valid on the date) |
| Save a rate | `sp_client_rate_save` |
| Delete a rate | `sp_client_rate_delete` |

**Overlap is rejected**: two rates for the same client+route whose validity windows overlap fail
`sp_client_rate_save`. This is what makes rate suggestion unambiguous.

The suggested rate is only a **default** at authorization — the actual price is snapshotted into
`service_requests.agreed_rate` and never recalculated (BR-04).

## Routes

`Route` = origin, destination, estimated km, description. `uq_routes_pair` makes
`(origin, destination)` unique. Ad-hoc destinations simply create a new route (decision D2);
`service_requests.route_id` is `NOT NULL`.

| Operation | Service | Permission |
| --- | --- | --- |
| Search / list all / find | `RouteService` | none |
| Save / delete | `RouteService` | `routes.write` |

Delete is blocked when the route has requests or rates (`sp_route_delete`).

## UI

- **Clients** tab: table (Nombre, RFC, Contacto, Tipo, Pago, Credito, Estado) + search; actions
  Nuevo / Editar / Desactivar / Activar / **Tarifas** / Eliminar. Double-click edits; Enter searches.
- **Tarifas** dialog: rates table with Nueva tarifa / Editar / Eliminar.
- **Rutas** tab: table + Nuevo / Editar / Eliminar.

## Java map

| Layer | Clients | Rates | Routes |
| --- | --- | --- | --- |
| Record | `Client` | `ClientRate` | `Route` |
| Repository | `ClientRepository` | `ClientRateRepository` | `RouteRepository` |
| Service | `ClientService` | `ClientService` (rate methods) | `RouteService` |
| View | `ClientsView` | (dialog inside `ClientsView`) | `RoutesView` |

Next: [Service requests](/features/requests/).
