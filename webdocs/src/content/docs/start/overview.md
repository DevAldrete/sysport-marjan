---
title: Overview
description: What SysPort-MARJAN does, the domain vocabulary and the mental model.
---

SysPort-MARJAN is a desktop application for **Transportes MARJAN**, a fictional trucking company
(built from a real requirements interview). It models the **entire service lifecycle** so any
operation can be reconstructed end to end.

> It is not about recording a trip. It is about **connecting everything around a trip**: who
> requested it, what was carried, which vehicle and operator ran it, what it cost, whether it was
> delivered, and whether it was paid.

## The domain in one diagram

```text
 client ── requests ──► service request ──1:1──► trip ──► vehicle
   │                        │                     │        employee (operator)
   │                        │                     ├─ expenses
   │                        │                     ├─ advances (cash to operator)
   │                        │                     ├─ fuel loads
   │                        │                     ├─ incidents
   │                        │                     └─1:1─ delivery
   │                        │
   └──── invoices ──◄───────┘
            │
            └─ payments
```

## The lifecycle, end to end

```text
requested ─► authorized ─► scheduled ─► assigned ─► in_transit ─► delivered ─► closed
    │            │             │            │
    └────────────┴─────────────┴────────────┴──► cancelled   (any time before in_transit)
```

Along the way:

- **authorized** snapshots the price into `agreed_rate` (never recalculated from tariffs).
- **assigned** creates the trip, picking a vehicle and an operator that are free and legal.
- **in_transit** records departure and flips the vehicle/operator to `on_trip`.
- **delivered** records proof of delivery; **closed** is allowed only when documentation is complete.
- Costs (expenses, fuel, advances) accumulate on the trip.
- An **invoice** is created from a delivered/closed request; **payments** settle it.

## Tech stack

| Concern | Choice |
| --- | --- |
| Language | Java 21 (records, sealed interfaces, pattern matching) |
| UI | Java Swing + FlatLaf |
| Database | MySQL 8.4 (Docker Compose) |
| Data access | Plain JDBC (no ORM) |
| Build | Maven |
| Tests | JUnit 5 |
| Docs (this site) | Astro + Starlight |

## Mental model: where things live

| Question | Answer | Where |
| --- | --- | --- |
| What are the rules? | Stored procedures & functions | `db/init/*.sql` |
| What does a rule allow? | A `fn_*` function or a `sp_*` procedure | `db/init/` |
| How does Java talk to the DB? | Repositories calling procedures | `*Repository.java` |
| Who can do what? | Services check permissions | `*Service.java` |
| What does the user see? | Swing views | `*View.java` |

## Glossary (short version)

| Term | Meaning |
| --- | --- |
| **Service request** | A client's order to move cargo A→B, identified by a folio (`SR-2026-000123`). |
| **Trip** | The physical execution of a request with a vehicle and an operator. |
| **Operator** | An employee who drives (an `employee` with a license). |
| **Advance** | Cash given to an operator before a trip, justified afterwards. |
| **Settlement** | Comparing an advance against proven expenses (BR-16). |
| **Folio** | Human-readable unique request identifier. |
| **Snapshot** | A value copied at a point in time (e.g. `agreed_rate`). |

The [full glossary](/reference/glossary/) lists every term. Next: [Local setup](/start/setup/).
