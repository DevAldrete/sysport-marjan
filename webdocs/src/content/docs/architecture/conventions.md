---
title: Conventions
description: Code style, naming, comments and the Git workflow this project expects.
---

## Code style

- **Methods short enough to read without scrolling**, one level of abstraction each.
- **Names in English.** Domain terms follow the [glossary](/reference/glossary/).
- **No inheritance deeper than one level.** Prefer composition and small interfaces.
- **No comments that restate code.** Comment **why**, and cite the rule: `// BR-21: an edit never lowers the odometer.`
- **No dead code, no speculative abstractions.** If it is not used, delete it.
- **Money is `BigDecimal`** (never `double`), mapped to `DECIMAL(12,2)`.
- **Dates use `java.time`** (`LocalDate`, `LocalDateTime`), never `java.util.Date`.
- **Always `PreparedStatement` with `?`.** Never concatenate user input into SQL.
- **Always try-with-resources** on `Connection` / `Statement` / `ResultSet` (handled by `Database`).
- Configuration comes from environment variables; **never** hard-code or commit secrets.

## Where code goes

| Kind of change | Location |
| --- | --- |
| A business rule, validation or calculation | `db/init/*.sql` (function/procedure) |
| A read/write use case | repository + service |
| A permission check | service |
| Screen layout, labels, dialogs | view |
| A pure helper reused by views | `shared/` |

If a rule cannot be expressed in SQL, it becomes a documented exception — and it still needs a test.

## Comments that earn their place

Good comments explain intent and cite rules:

```java
/** BR-19: cancel a pending/overdue invoice that has no payments yet. */
public Result<Void> cancel(long id) { … }
```

```sql
-- BR-21: an edit never lowers the odometer.
UPDATE vehicles SET …, mileage = GREATEST(mileage, COALESCE(p_mileage, mileage))
```

## Git workflow

### Branches

- `main` is always runnable.
- Short-lived branches: `feat/trip-assignment`, `fix/fuel-odometer-check`, `docs/prd-update`,
  `chore/maven-setup`.
- Merge when green, delete the branch after.

### Commits

[Conventional Commits](https://www.conventionalcommits.org/):

```text
<type>(<scope>): <imperative summary, ≤ 72 chars>

<optional body: WHY, not what. Reference rules, e.g. "Implements BR-05">
```

Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`, `perf`, `style`.
Scopes: package names (`trips`, `finance`, `security`, `shared`, `db`, …).

### What "atomic and contextual" means here

- **One logical change per commit.** It compiles, tests pass, and it can be reverted alone.
- **Never mix** refactoring with behaviour changes, or formatting with logic.
- **A rule and its test go in the same commit.**
- **Schema changes are their own commit** (`feat(db): add planned window to trips`) so the history
  shows why a table changed.
- Review with `git diff --staged` before every commit.

Example history for one slice:

```text
feat(db): add clients and client_rates tables
feat(clients): add Client and ClientRate records
feat(clients): add ClientRepository with JDBC CRUD
test(clients): cover RFC validation (BR-02)
feat(clients): validate RFC format in ClientRules
feat(clients): add ClientService with permission checks
feat(shared): add generic RecordTableModel
feat(clients): add clients table view and form dialog
```

### Housekeeping

- `.gitignore`: `target/`, `.env`, `*.iml`, `.idea/`, `*.log`.
- Commit `.env.example`, never `.env`.
- Never commit secrets or real personal data — seed data is fake.

## Definition of done (per feature)

- [ ] Schema change committed (if any) and `SYSPORT_MARJAN.sql` regenerated
- [ ] Records, repository, service, view implemented following the layer rules
- [ ] Business rules covered by tests, each referencing its `BR-xx`
- [ ] No SQL outside repositories; no DB calls on the EDT
- [ ] Permission checks in the service layer
- [ ] Manually exercised through the UI with seed data
- [ ] Atomic commits with conventional messages

Next: [Database schema](/database/schema/).
