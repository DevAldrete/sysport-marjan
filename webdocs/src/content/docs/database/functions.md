---
title: Functions
description: The 24 pure functions in 02-functions.sql — the vocabulary of the business rules.
---

Functions in `db/init/02-functions.sql` are **pure**: no I/O, no side effects, one clear answer.
Procedures call them. This is where the rule vocabulary lives, so it is the best place to start
reading the domain.

## State & eligibility

| Function | Returns | Rule |
| --- | --- | --- |
| `fn_request_can_transition(p_from, p_to)` | 1/0 | **BR-03** — the request state machine. Allowed: forward moves (`requested→authorized→scheduled→assigned→in_transit→delivered→closed`) and `→cancelled` from any pre-transit state. |
| `fn_request_reschedulable(p_status)` | 1/0 | **BR-03** — true for `authorized`, `scheduled`, `assigned`, so a date typo is fixable before transit. |
| `fn_vehicle_assignable(p_status)` | 1/0 | **BR-07 / BR-11** — true only for `available`. |
| `fn_employee_assignable(p_status)` | 1/0 | **BR-09** — true only for `available`. |
| `fn_request_weight(p_request_id)` | decimal | **BR-08** — effective weight: `SUM(quantity × unit_weight)` over `request_packages`, else the manual `estimated_weight`. |
| `fn_capacity_ok(p_capacity, p_weight)` | 1/0 | **BR-08** — `capacity ≥ weight` (either null → true). |

## Money & finance

| Function | Returns | Rule |
| --- | --- | --- |
| `fn_amount_matches(p_liters, p_price, p_amount, p_tolerance)` | 1/0 | **BR-18** — `abs(liters×price − amount) ≤ tolerance` (default 0.05). |
| `fn_advance_balance(p_given, p_expenses, p_fuel)` | decimal | **BR-16** — `given − expenses − fuel`. |
| `fn_invoice_due_date(p_issue, p_terms, p_credit_days)` | date | **BR-19** — cash → `issue`; credit → `issue + credit_days`. |
| `fn_invoice_status(p_current, p_amount, p_paid, p_due, p_today)` | varchar | **BR-19** — cancelled stays cancelled; paid if `paid ≥ amount`; overdue if `due < today`; else pending. |

## Identifiers

| Function | Returns | Rule |
| --- | --- | --- |
| `fn_next_folio(p_year)` | `SR-YYYY-NNNNNN` | **BR-01** — max existing number for the year + 1. |
| `fn_next_invoice_number(p_year)` | `INV-YYYY-NNNNNN` | **BR-20** — max existing number for the year + 1. |

## Format validators

Used by the save procedures to reject bad input before it reaches the table. Each returns 1/0.

| Function | Accepts |
| --- | --- |
| `fn_rfc_valid` | `^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$` |
| `fn_email_valid` | a valid email shape |
| `fn_phone_valid` | `^[0-9+(). -]{7,20}$` |
| `fn_curp_valid` | `^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$` |
| `fn_plates_valid` | `^[A-Z0-9-]{4,10}$` |
| `fn_license_number_valid` | `^[A-Za-z0-9-]{4,30}$` |

## Range validators

Match the `DECIMAL` widths so a value can never overflow a column.

| Function | Range |
| --- | --- |
| `fn_money_valid` | `DECIMAL(12,2)`, 0 … 9 999 999 999.99 (null ok) |
| `fn_measure_valid` | `DECIMAL(10,1)`, 0 … 999 999 999.9 (null ok) |
| `fn_liters_valid` | `DECIMAL(8,2)`, >0 … 999 999.99, not null |
| `fn_price_per_liter_valid` | `DECIMAL(8,3)`, >0 … 99 999.999, not null |
| `fn_date_valid` | 2000-01-01 … 2100-01-01 (null ok) |
| `fn_year_valid` | 1950 … `YEAR(CURDATE())+1` (null ok) |

:::tip
Reading order: start with `fn_request_can_transition` (the lifecycle) and
`fn_invoice_status` (derived state), then follow a save procedure such as `sp_vehicle_save` to see
the validators applied.
:::

Next: [Stored procedures](/database/procedures/).
