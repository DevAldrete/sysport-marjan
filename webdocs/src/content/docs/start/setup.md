---
title: Local setup
description: Get the database, the API and the web client running in a few minutes.
---

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Node | 22+ | `node -v` |
| Docker | with Compose | Docker Desktop or OrbStack |

## Quick start (Makefile)

Everything is wrapped in a small `Makefile`. Run `make help` to list the tasks.

```bash
make setup     # create .env from .env.example and install frontend dependencies
make db-up     # start MySQL (schema and seed load automatically on first run)
make api       # build and run the API on http://localhost:8080
make web       # in a second terminal: Vue dev server on http://localhost:5173
```

Open <http://localhost:5173> and log in with the default dev user (from seed data):

```text
usuario:  admin
password: admin123
```

:::danger
`admin123` is development-only. Never use it anywhere real.
:::

Prefer containers for the whole stack:

```bash
make up        # mysql + api + nginx serving the built PWA at http://localhost:8081
make down
```

## Configuration

`.env` holds the database credentials and JWT secrets read by Docker Compose (and,
indirectly, by the API through the environment):

```dotenv
DB_ROOT_PASSWORD="rootchangeme"
DB_USER="marjan"
DB_PASSWORD="changeme"
DB_NAME="sysportdb"
JWT_SECRET="sysport-dev-access-secret-please-change-32bytes"
JWT_REFRESH_SECRET="sysport-dev-refresh-secret-please-change-32bytes"
```

:::caution
The `.env` values are quoted. If you ever parse this file yourself, strip the quotes.
`.env` is git-ignored — never commit it.
:::

`api/src/main/resources/application.yml` reads the same variables. When `DB_URL` is not set
the URL is assembled from `DB_HOST` / `DB_PORT` / `DB_NAME`.

## Database commands

```bash
make db-up       # start
make db-down     # stop (data is kept)
make db-reset    # stop AND wipe the volume (re-runs db/init)
```

:::note
`db/init/*.sql` only run when the data volume is empty. After changing the schema during
development, reset with `make db-reset`. See [Schema changes](/database/migrations/).
:::

## Tests

```bash
make test        # fast unit tests, no database
make db-reset
make test-it     # adds the integration tests (procedures + HTTP API) against MySQL
```

`test-it` sets `SYSPORT_IT=1`; those tests expect a freshly seeded database.

## IDE tips

- Import the Maven aggregator (`pom.xml`) — it holds `core` and `api`.
- Run configuration: main class `mx.marjan.api.Application` (module classpath includes dependencies).
- `make api` is the quickest way to run the server locally.
- `SYSPORT_MARJAN.sql` at the repo root is a **generated** single-file bootstrap
  (`db/build-bootstrap.sh` concatenates `db/init/*.sql`). Regenerate it after schema edits.

## Useful manual SQL

```bash
# open a shell inside the container
docker exec -it sysportdb mysql -uroot -prootchangeme sysportdb

# run one file against the running database
docker exec -i sysportdb mysql -uroot -prootchangeme < db/init/05-views.sql
```

## Documentation site

```bash
make docs        # Astro/Starlight dev server on http://localhost:4321
```

Next: [Architecture overview](/architecture/overview/).
