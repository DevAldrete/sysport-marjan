---
title: Testing
description: How the test suites are organized and how to run them.
---

Because the **business rules live in the database**, the most valuable tests call the stored
procedures directly against a real MySQL. Those are opt-in so the default build stays fast.

## Two suites

| Command | What runs | Needs a database? |
| --- | --- | --- |
| `mvn test` | Unit tests (pure Java) | No |
| `SYSPORT_IT=1 mvn test` | Unit tests **+ integration tests** | Yes, freshly seeded |

Integration tests are annotated with
`@EnabledIfEnvironmentVariable(named = "SYSPORT_IT", matches = "1")`, so without the variable they
are skipped, not failed.

```bash
# fast loop while coding
mvn test

# full run: reset the DB first so seed ids are predictable
docker compose down -v && docker compose up -d
SYSPORT_IT=1 mvn test

# a single test
SYSPORT_IT=1 mvn test -Dtest=SqlRulesTest
```

## What each test class covers

| Test class | Type | Covers |
| --- | --- | --- |
| `rules/SqlRulesTest` | Integration | The lifecycle end to end: BR-01 folios, BR-04 overlapping rates, BR-05 double-booking, BR-13 close-with-documents, BR-19 payments/overpayment, BR-21 mileage, guarded deletes. |
| `rules/DatabaseMessagesTest` | Unit | `Database.translate`: duplicate key, foreign key, connection failure, check constraint. |
| `rules/DataErrorsTest` | Unit | Coarse mapping of common driver error codes. |
| `rules/ValidatorsTest` | Unit | Username format. |
| `shared/FormModelTest` | Unit | Form values, computed fields and the live date/money/number validators. |
| `shared/TextTest` | Unit | `Text.truncate` behaviour. |

## Writing an integration test

`SqlRulesTest` connects with plain JDBC (not through the app's `Database`), calls procedures with
`CallableStatement`, and asserts on the `OUT p_problems` value: **`null` means success, non-null is
the rule's rejection message**.

```java
@Test
void vehicleCannotBeDoubleBooked() throws Exception {
    // ... create/authorize/schedule two requests in an overlapping window ...
    Object problems = call("{call sp_assign_trip(?,?,?,?,?,?)}",
            new int[] { Types.VARCHAR, Types.BIGINT }, second, 10L, 10L, 1L)[0];
    assertNotNull(problems, "BR-05: overlapping vehicle window must be rejected");
}
```

:::caution
Integration tests mutate the seed data (they create requests and trips). Run them against a
**freshly seeded** database so the seed ids they rely on (client 2, route 4, vehicles 5/8/10,
operators 7/8/10) still exist. Reset with `docker compose down -v && docker compose up -d`.
:::

:::note
Testcontainers 1.16.1 cannot detect the OrbStack Docker socket on the original dev machine, which
is why integration tests use the Compose MySQL instead of an ephemeral container. If you upgrade
Testcontainers and Docker, this could be revisited.
:::

## The rule

**Every business rule (`BR-xx`) gets at least one test, and the commit that adds the rule includes
its test.** Rules are named `AssignmentRulesTest.rejectsVehicleWithOverlappingTrip()` — the test
name states the expectation.

## What is *not* tested

- JavaFX screens: exercised manually with seed data (see the PRD's manual checklist).
- Permissions: covered indirectly; the service checks are one line each.

Next: [Architecture overview](/architecture/overview/).
