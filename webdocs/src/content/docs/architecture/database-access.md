---
title: Database access
description: The Database class, Result, error translation, id allocation and how transactions work.
---

All database access goes through one class: **`mx.marjan.shared.Database`**. Repositories never
open connections themselves; they call `Database.call*`.

## Connection

```java
Database.url()       // DB_URL, or built from DB_HOST/DB_PORT/DB_NAME
Database.user()      // DB_USER      (default marjan)
Database.password()  // DB_PASSWORD  (default changeme)
Database.getConnection()
Database.testConnection()   // used at startup to fail fast
```

Everything is read from environment variables. Nothing is hard-coded, nothing is committed.
`DriverManager` sits behind `Database`; swapping in a pool later changes only this class.

## The call helpers

Every helper runs the statement in try-with-resources and wraps any `SQLException` in a
`DataException` whose message is already user-friendly (see [error translation](#error-translation)).

| Helper | Procedure shape | Returns |
| --- | --- | --- |
| `callList(sql, mapper, params…)` | returns a result set | `List<T>` |
| `callOne(sql, mapper, params…)` | returns a result set | `Optional<T>` |
| `callNoOut(sql, params…)` | no OUT params | `void` |
| `callVoid(sql, params…)` | `OUT p_problems TEXT` | `Result<Void>` |
| `callForId(sql, params…)` | `OUT p_id BIGINT, OUT p_problems TEXT` | `Result<Long>` |
| `callForProblemsAndId(sql, params…)` | `OUT p_problems TEXT, OUT p_id BIGINT` | `Result<Long>` |
| `callReport(reader, sql, params…)` | raw result set | `T` |
| `call(sql, outTypes, params…)` | anything | `Object[]` of OUT values |

```java
// read
public List<Client> search(String term) {
    return Database.callList("{call sp_clients_search(?)}", this::map, term);
}

// write with a problems OUT parameter
public Result<Void> delete(long id) {
    return Database.callVoid("{call sp_client_delete(?,?)}", id);
}
```

:::danger
The number of `?` in the call string must equal **IN parameters + OUT parameters**. Java's
`CallableStatement` does not verify this against the procedure signature, so a mismatch is a
runtime error. When you change a procedure, grep for its callers.
:::

## `Result<T>` — the outcome type

Normal business flow never throws. It returns a sealed `Result`:

```java
public sealed interface Result<T> permits Result.Ok, Result.Err {
    record Ok<T>(T value) implements Result<T> {}
    record Err<T>(List<String> problems) implements Result<T> {}
    // ok(...), err(...), isOk(), isErr(), value(), problems()
}
```

- `Result.ok(value)` — success, optionally with a payload.
- `Result.err("message")` or `Result.err(List.of(…))` — one or more problems.
- Problems come from two places: the service's permission check, or the procedure's
  `p_problems` text. The UI shows them verbatim.

## Error translation

Raw JDBC errors are ugly. `Database.translate(SQLException)` turns them into plain Spanish, using
the SQLState, the MySQL error code, and the constraint/index name parsed from the message:

| Situation | Message |
| --- | --- |
| Duplicate key (1062) | `Ya existe un cliente con ese RFC ("ABC950101XYZ").` |
| Missing FK (1452) | `No se puede facturar: la solicitud no existe.` |
| Check constraint (3819) | `Uno de los valores no cumple las reglas permitidas.` |
| DB unreachable (SQLState `08…`) | `No se pudo conectar con la base de datos. …` |
| Unknown | `Ocurrio un error inesperado al acceder a los datos (codigo N).` |

The `UNIQUE_MESSAGES` and `FOREIGN_KEY_MESSAGES` maps name the field/relationship. `Ui.failure`
shows `DataException`s under the title *"No se pudo completar la operacion"*.

## Transactions

:::note
Transactions are **inside the stored procedures**, not in Java. There is no
`Database.inTransaction` helper. Procedures that must be atomic (assignment, payments, cascades)
declare `START TRANSACTION` / `COMMIT` and a `DECLARE EXIT HANDLER FOR SQLEXCEPTION` that rolls
back and sets `p_problems`.
:::

This is deliberate: the rule and its transaction live in the same place, and concurrency
(`SELECT … FOR UPDATE`) is handled where the data is.

## Id allocation (no `AUTO_INCREMENT`)

Every primary key is allocated by the `sequences` table through `sp_next_id(table, OUT id)`:

```sql
UPDATE sequences SET next_value = LAST_INSERT_ID(next_value + 1) WHERE name = p_name;
SET p_id = LAST_INSERT_ID();
```

The `LAST_INSERT_ID` read-back is atomic, so two concurrent writers never receive the same id. If a
sequence row is missing, `sp_next_id` self-primes from `MAX(id)` of the table.

**Folios** (`SR-2026-000123`, `INV-2026-000001`) are computed by `fn_next_folio` /
`fn_next_invoice_number` from the max existing number for the year. To avoid two creators racing on
the same `MAX+1`, the create procedures call `sp_next_id` **first**, holding the sequence row lock
until commit.

## The `DELIMITER` gotcha

`db/init/*.sql` use `DELIMITER $$` … `DELIMITER ;` around routines with `BEGIN … END` bodies.
`DELIMITER` is a **mysql-client directive**, not SQL. Consequences:

- Docker's init loader and the `mysql` CLI understand it.
- **JDBC cannot load these files directly** — you cannot feed `db/init/*.sql` through
  `Database.call`. Load them with the client (Docker does this automatically).

## Manual bootstrap

`SYSPORT_MARJAN.sql` is a generated single file (schema + seed) for running everything at once:

```bash
db/build-bootstrap.sh          # regenerate from db/init/*.sql
mysql -u root -p < SYSPORT_MARJAN.sql
```

Regenerate it whenever `db/init/` changes, and commit it. See
[Schema changes](/database/migrations/).

Next: [UI toolkit](/architecture/ui-toolkit/).
