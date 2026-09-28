---
title: Views
description: The 8 read projections in 05-views.sql that keep queries consistent.
---

Views in `db/init/05-views.sql` are the **read model**. Search and by-id procedures do
`SELECT * FROM v_*` instead of repeating a 15-column `SELECT`, so a column change happens in one
place. They also join in the human-readable labels the UI needs.

## `v_client`

All client business columns (no timestamps): `id, name, rfc, address, phone, email, contact_name,
client_type, payment_terms, credit_limit, credit_days, status`.

## `v_route`

`id, origin, destination, estimated_km, description, route_label`, where `route_label` is
`fn_route_label(id)` (the ordered stops, e.g. `A -> B -> C`) falling back to
`origin -> destination` (BR-26).

## `v_vehicle`

`id, internal_code, plates, brand, model, year, serial_number, vehicle_type, load_capacity,
mileage, status`.

## `v_employee`

`employees e LEFT JOIN licenses l ON l.id = e.license_id` — employee fields plus the license as
`license_id, license_number, license_type, issue_date, expiration_date`. The `LEFT JOIN` means an
employee without a license still appears.

## `v_service_request`

`service_requests JOIN clients JOIN routes`, adding:

- `client_name` (from `clients.name`)
- `route_label` = `COALESCE(fn_route_label(sr.route_id), CONCAT(r.origin, ' -> ', r.destination))`
- `package_count` and `package_weight` (= `SUM(quantity × unit_weight)` over `request_packages`)

Used by every request search/by-id/status/pending-billing procedure. `estimated_weight` stays the
manual fallback; the effective weight the UI validates against is
`fn_request_weight(sr.id)` = `COALESCE(package_weight, estimated_weight)`. Note the view joins
`request_packages` with **correlated subqueries**, so a request with no packages still lists
(the count is 0, the weight null).

## `v_trip`

`trips JOIN service_requests JOIN clients JOIN routes JOIN vehicles JOIN employees`, adding:

- `folio` (request), `client_name`, `route_label`
- `vehicle_label` = `internal_code (plates)`
- `employee_name`

This is why `Trip` in Java has `folio`, `clientName`, `vehicleLabel`, `employeeName` even though
`trips` stores only ids.

## `v_fuel_load`

`fuel_loads JOIN vehicles` (`vehicle_label`) `LEFT JOIN trips LEFT JOIN service_requests`
(`folio`). A load without a trip still lists (the joins are outer).

## `v_invoice`

`invoices JOIN clients JOIN service_requests`, plus a **derived** column:

```sql
(SELECT COALESCE(SUM(p.amount), 0) FROM payments p WHERE p.invoice_id = i.id) AS paid
```

So `paid` is always consistent with the payments table — it is never stored on `invoices`.
The Java `Invoice.balance()` is `amount − paid`.

:::tip
If a list screen shows a blank where you expect a label, check the view: the join that provides the
label may be filtering the row out (use `LEFT JOIN` when the relation is optional, as `v_fuel_load`
and `v_employee` do).
:::

Next: [Seed data & roles](/database/seed/).
