---
title: Troubleshooting
description: Common problems and how to fix them.
---

## "No se pudo conectar a la base de datos"

The app checks the connection at startup and exits with this dialog.

```bash
docker compose ps          # is mysql running and healthy?
docker compose up -d
docker compose logs -f mysql
```

- Port 3306 already in use (a local MySQL)? Stop it or change `DB_PORT`.
- Wrong credentials? Check `.env` (values are quoted — strip quotes if you parse it yourself).

## My schema change did nothing

`db/init/*.sql` only run on an **empty volume**.

```bash
docker compose down -v && docker compose up -d
```

This wipes and rebuilds the database. See [Schema changes](/database/migrations/).

## Integration tests are skipped

They are opt-in:

```bash
docker compose down -v && docker compose up -d
SYSPORT_IT=1 mvn test
```

Without `SYSPORT_IT=1` the 7 `SqlRulesTest` cases are skipped, not failed. If they **fail**, you are
probably running against a database that is not freshly seeded (they rely on seed ids such as client
2, route 4, vehicles 5/8/10).

## "You have an error in your SQL syntax near 'DELIMITER'"

You fed `db/init/*.sql` through JDBC. `DELIMITER` is a mysql-client directive, not SQL. Load these
files with the `mysql` CLI (Docker does it automatically); JDBC cannot.

## A procedure call fails at runtime with a parameter count error

The `?` count in the call string must equal **IN parameters + OUT parameters**. Java cannot verify
this at compile time. When you change a procedure signature, grep for its callers:

```bash
grep -rn "sp_vehicle_save" src/main/java
```

## "No tiene permiso para…" even as admin

Permissions are loaded **at login**. If you changed a role or its permissions, log out and back in.
Verify the role's permissions:

```sql
SELECT p.name FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
WHERE rp.role_id = 1;
```

## The UI freezes during a query

Database work must run off the FX Application Thread via `Async.run(...)` / `BaseView.load(...)`.
If a view calls a service directly on the FX thread, it blocks the UI. Move the call into `Async.run`.

## A duplicate/foreign-key error is shown instead of a friendly message

`Database.translate` maps common errors to plain Spanish. If a new constraint is not in
`UNIQUE_MESSAGES` / `FOREIGN_KEY_MESSAGES`, add it so users see the field name instead of a generic
message. See [Database access](/architecture/database-access/#error-translation).

## Ids or folios look wrong

Ids come from the `sequences` table via `sp_next_id` (no `AUTO_INCREMENT`). The seed primes each
sequence to `MAX(id)`. If you inserted rows by hand with explicit ids, re-prime:

```sql
UPDATE sequences SET next_value = (SELECT COALESCE(MAX(id),0) FROM clients) WHERE name = 'clients';
```

## Known gaps / tech debt

:::caution
These are real issues found while writing this wiki. They are harmless today but should be cleaned up:

- **`FuelLoadRepository.vehicleIdForTrip` is dead code and its procedure is missing.**
  It calls `sp_trip_vehicle(?)`, which is **not defined** in `db/init/`. The method is never called
  from anywhere, so it never fails — but if you start using it, it will throw. Either add the
  procedure (`SELECT vehicle_id FROM trips WHERE id = ?`) or delete the method.
- The `docs/` path and `PRD.md` sometimes describe planned classes (`AssignmentRules`,
  `ClientRules`, `Database.inTransaction`) that were deliberately **not** created: those rules live
  in the database and transactions live inside procedures. Treat the code as the source of truth.
:::

## Getting help

- Search the codebase for the rule id (`BR-xx`) or the screen name.
- The [Java class map](/reference/java-map/) and [Stored procedures](/database/procedures/) index
  everything.
