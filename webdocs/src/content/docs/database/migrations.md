---
title: Schema changes
description: How db/init is loaded, how to apply a change, and what to commit.
---

## How the database gets built

Docker Compose mounts `db/init/` into the container's init directory:

```yaml
volumes:
  - ./db/init:/docker-entrypoint-initdb.d:ro
```

MySQL runs **every `.sql` file in filename order** the first time the data volume is empty. The
numeric prefixes define the order, and each file starts with `USE sysportdb;` so it also works when
loaded manually.

```text
01-tables.sql       database + 22 tables
02-functions.sql    22 rule functions + sp_next_id
05-views.sql        8 views
10-security.sql     users, roles, permissions, audit
20-clients.sql      clients, rates, routes
30-fleet.sql        vehicles, fuel, maintenance
40-operators.sql    employees, licenses
50-requests.sql     service requests + lifecycle
60-trips.sql        assignment, trips, deliveries, incidents
70-costs.sql        expenses, advances
80-finance.sql      invoices, payments
90-reports.sql      report + dashboard queries
99-seed.sql         roles, permissions, demo data
```

:::caution
Init scripts **only run on an empty volume**. Editing a file does nothing to a running database.
:::

## Applying a change

```bash
# edit db/init/...
db/build-bootstrap.sh                 # regenerate SYSPORT_MARJAN.sql
docker compose down -v && docker compose up -d
```

`docker compose down -v` wipes the volume; `up -d` rebuilds from scratch. During development this
is the intended workflow — there are no incremental migration files.

:::note
The database can be wiped and re-seeded at any time. Treat `db/init/` as the source of truth, not a
running database.
:::

## Adding a new domain file

If you add a new area, create `db/init/<NN>-<name>.sql` with a number that puts it after its
dependencies (tables in `01`, functions in `02`, views in `05`, then domain files, seed last). Add
`USE sysportdb;` at the top. Re-run `db/build-bootstrap.sh`.

## The `DELIMITER` rule

Files with routines use:

```sql
DELIMITER $$
CREATE PROCEDURE ... BEGIN ... END$$
DELIMITER ;
```

`DELIMITER` is a **client** directive, understood by the `mysql` CLI and Docker's loader — **not by
JDBC**. You cannot load these files through `Database.call`. Load them with the client.

## Regenerating `SYSPORT_MARJAN.sql`

`SYSPORT_MARJAN.sql` at the repo root is a **generated** concatenation of `db/init/*.sql`, for
running everything in one shot (`mysql -u root -p < SYSPORT_MARJAN.sql`). Always regenerate and
commit it after editing `db/init/`:

```bash
db/build-bootstrap.sh
```

## Committing schema changes

Schema changes are their **own commit**, before the Java that uses them:

```bash
git add db/ SYSPORT_MARJAN.sql
git commit -m "feat(db): add planned window to trips"

git add src/
git commit -m "feat(trips): store the planned window on assignment"
```

This keeps the history honest: you can see *why* a table changed, separately from how the app uses
it. See [Conventions](/architecture/conventions/#commits).

## Checklist

- [ ] Edited the right `db/init/<NN>-*.sql` (respecting order)
- [ ] `USE sysportdb;` present in new files
- [ ] `db/build-bootstrap.sh` run and `SYSPORT_MARJAN.sql` committed
- [ ] `docker compose down -v && up -d` applied it cleanly
- [ ] `SYSPORT_IT=1 mvn test` still green
- [ ] Java repositories updated if a procedure signature changed

Next: [Lifecycles](/domain/lifecycle/).
