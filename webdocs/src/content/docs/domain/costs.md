---
title: Costs & advances
description: Trip expenses, operator advances and their settlement, and fuel loads.
---

Three cost sources attach to a trip. Knowing which is which matters for reports and settlement.

## Expenses — `expenses`

Per-trip costs typed by category:

| `expense_type` | Label |
| --- | --- |
| `tolls` | Casetas |
| `food` | Alimentos |
| `parking` | Estacionamiento |
| `lodging` | Hospedaje |
| `repairs` | Reparaciones |
| `handling` | Maniobras |
| `permits` | Permisos |
| `other` | Otro |

Validation (**BR-17**): type in the enum, `amount > 0`, date required — `sp_validate_expense`.
Registered via `sp_expense_save`; read with `sp_expenses_by_trip`.

:::note
There is **no `fuel` expense type**. Fuel cost lives only in `fuel_loads` (decision D3), so it is
never double-counted.
:::

## Advances — `advances`

Cash handed to an operator before a trip, to be justified afterwards.

| Field | Meaning |
| --- | --- |
| `amount_given` | cash given (> 0) |
| `delivered_date` | when it was handed over |
| `status` | `pending` → `settled` |
| `settled_at`, `settled_by` | who and when it was settled |

## Settlement — BR-16

```text
balance = amount_given − (Σ expenses.amount + Σ fuel_loads.amount)   for the trip

balance > 0  → the operator must return the difference   (OperatorOwes)
balance < 0  → the company must reimburse |balance|      (CompanyOwes)
balance = 0  → settled
```

Computed by `sp_advance_balance` (OUT `given`, `proven`, `balance`), which uses
`fn_advance_balance`. `sp_settle_advance` marks it settled (only from `pending`).

In Java this is modelled explicitly:

```java
public record AdvanceBalance(BigDecimal given, BigDecimal proven, Outcome outcome) {
    public sealed interface Outcome permits Settled, OperatorOwes, CompanyOwes {
        record Settled() implements Outcome {}
        record OperatorOwes(BigDecimal amount) implements Outcome {}
        record CompanyOwes(BigDecimal amount) implements Outcome {}
    }
    public String label() { /* "Comprobado" / "El operador debe devolver N" / … */ }
}
```

The sign of `balance` selects the `Outcome`; the UI shows `label()`.

## Fuel — `fuel_loads`

A fuel purchase, optionally tied to a trip:

| Field | Meaning |
| --- | --- |
| `vehicle_id` | which unit |
| `trip_id` | optional; when set, must match the trip's vehicle |
| `liters`, `price_per_liter`, `amount` | quantity, unit price, total |
| `odometer_reading` | dashboard reading at the load |

Validation (**BR-18**, `sp_validate_fuel_load`):

- liters, price and amount are valid and > 0;
- `amount ≈ liters × price_per_liter` within **±0.05** (`fn_amount_matches`);
- the odometer does not go **below** the vehicle's current mileage;
- if a trip is given, the load's vehicle must equal the trip's vehicle.

:::tip
The UI computes `Importe = litros × precio` live in a read-only field, because the database rejects
a mismatch — typing it by hand was error-prone.
:::

`sp_fuel_save` inserts the load and, when the odometer is higher, advances `vehicles.mileage`
(**BR-21**) — all in one transaction.

## Where cost shows up

| Screen | Shows |
| --- | --- |
| Trip detail → Resumen | total expenses, total fuel, advance settlement |
| Trip detail → Gastos / Anticipos / Combustible | per-type tables |
| Report "Rentabilidad por viaje" | `agreed_rate − expenses − fuel` |
| Report "Rendimiento de combustible" | km per liter |

Next: [Invoicing & payments](/domain/invoicing/).
