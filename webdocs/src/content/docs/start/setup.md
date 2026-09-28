---
title: Local setup
description: Get the database and the app running on your machine in five minutes.
---

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| Docker | with Compose | Docker Desktop or OrbStack |

## 1. Clone and configure

```bash
git clone https://github.com/DevAldrete/sysport-marjan marjan
cd marjan
cp .env.example .env
```

`.env` holds the database credentials read by Docker Compose (and, indirectly, by the app):

```dotenv
DB_ROOT_PASSWORD="rootchangeme"
DB_USER="marjan"
DB_PASSWORD="changeme"
DB_NAME="sysportdb"
```

:::caution
The `.env` values are quoted. If you ever parse this file yourself, strip the quotes.
`.env` is git-ignored — never commit it.
:::

## 2. Start the database

```bash
docker compose up -d
```

On the **first** start, MySQL runs every script in `db/init/` in filename order (tables, functions,
views, security, domains, reports, then seed data). This creates the `sysportdb` database, the
`marjan` user, 23 tables, 24 functions, 112 procedures, 8 views and demo rows.

```bash
docker compose logs -f mysql   # watch it come up
```

## 3. Run the app

```bash
mvn compile exec:java
```

The `exec-maven-plugin` runs `mx.marjan.App`. It first checks the connection, then shows the login
dialog. Default dev credentials (from seed data):

```text
usuario:  admin
password: admin123
```

:::danger
`admin123` is development-only. Never use it anywhere real.
:::

## Database commands

```bash
docker compose up -d           # start
docker compose logs -f mysql   # follow logs
docker compose down            # stop (data is kept)
docker compose down -v         # stop AND wipe the volume (re-runs db/init)
```

:::note
`db/init/*.sql` only run when the data volume is empty. After changing the schema during
development, reset with `docker compose down -v && docker compose up -d`. See
[Schema changes](/database/migrations/).
:::

## Connection settings

`mx.marjan.shared.Database` reads these environment variables (defaults shown):

| Variable | Default |
| --- | --- |
| `DB_URL` | `jdbc:mysql://localhost:3306/sysportdb?sslMode=DISABLED&allowPublicKeyRetrieval=true` |
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_NAME` | `sysportdb` |
| `DB_USER` | `marjan` |
| `DB_PASSWORD` | `changeme` |

`DB_URL` wins when set; otherwise the URL is assembled from host/port/name.

## Tests (quick)

```bash
mvn test                    # fast unit tests, no database
SYSPORT_IT=1 mvn test       # adds the DB integration tests (needs a freshly seeded DB)
```

Full details on [Testing](/start/testing/).

## IDE tips

- Import as a **Maven** project; source/target is Java 21.
- Run configuration: main class `mx.marjan.App` (module classpath includes dependencies).
- The `target/` directory is build output; do not edit it.
- `SYSPORT_MARJAN.sql` at the repo root is a **generated** single-file bootstrap
  (`db/build-bootstrap.sh` concatenates `db/init/*.sql`). Regenerate it after schema edits.

## Useful manual SQL

```bash
# open a shell inside the container
docker exec -it sysportdb mysql -uroot -prootchangeme sysportdb

# run one file against the running database
docker exec -i sysportdb mysql -uroot -prootchangeme < db/init/05-views.sql
```

Next: [Your first change](/start/first-change/).
