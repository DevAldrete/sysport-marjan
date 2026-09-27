---
title: Finance
description: Expenses, advances, invoices and payments — where each is edited and viewed.
---

Package `mx.marjan.finance`. It covers four entities that are edited from two places: the **trip
detail** (expenses, advances) and the **Invoices** tab (invoices, payments).

## Where things are edited

| Entity | Edited from | Service | Permission |
| --- | --- | --- | --- |
| Expense | Trip detail → Gastos | `ExpenseService` | `expenses.write` |
| Advance | Trip detail → Anticipos | `AdvanceService` | `advances.write` |
| Invoice | Invoices tab | `InvoiceService` | `invoices.write` |
| Payment | Invoices tab → Pagos | `InvoiceService` | `payments.write` |

Reads (`listByTrip`, `paymentsFor`, `find`, `balanceForTrip`) have no permission gate.

## Invoices screen

`InvoicesView` is a vertical split:

- **Top:** invoices (Factura, Cliente, Solicitud, Importe, Pagado, Saldo, Vence, Estado) with filters
  (Estado, Cliente) and actions **Facturar, Registrar pago, Pagos, Cancelar, Actualizar estatus,
  Eliminar**.
- **Bottom:** "Por facturar" — requests with an authorized rate and no invoice
  (`sp_requests_pending_billing`, FR-INV-1) with **Facturar seleccionada**.

Double-click an invoice → payments dialog (list + delete). Double-click a pending row → bill it.

## Records & enums

| Type | Fields |
| --- | --- |
| `Invoice` | id, client, request folio, number, amount, issue/due dates, status, paid; `balance()` |
| `Payment` | id, invoice, amount, date, method |
| `Expense` | id, trip, type, amount, date, description |
| `Advance` | id, trip, employee, amountGiven, deliveredDate, status, settledAt |
| `AdvanceBalance` | given, proven, sealed `Outcome` (Settled / OperatorOwes / CompanyOwes) |
| `InvoiceStatus` | pending, paid, overdue, cancelled |
| `PaymentMethod` | cash, transfer, check, card, other |
| `ExpenseType` | tolls, food, parking, lodging, repairs, handling, permits, other |
| `AdvanceStatus` | pending, settled |

## Key rules

- **BR-16** advance settlement — see [Costs & advances](/domain/costs/#settlement-br-16).
- **BR-17** expense type/amount.
- **BR-18** fuel consistency (fuel lives in `fleet`, not here).
- **BR-19** invoice status, due dates, no overpayment, cancellation rules.
- **BR-20** one invoice per request, only for delivered/closed.

Full detail in [Invoicing & payments](/domain/invoicing/).

## Java map

| Layer | Class |
| --- | --- |
| Repositories | `ExpenseRepository`, `AdvanceRepository`, `InvoiceRepository`, `PaymentRepository` |
| Services | `ExpenseService`, `AdvanceService`, `InvoiceService` |
| View | `InvoicesView` (invoices/payments); trip detail hosts expenses/advances |

Next: [Reports](/features/reports/).
