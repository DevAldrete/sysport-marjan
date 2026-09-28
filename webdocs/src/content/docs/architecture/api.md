---
title: The API
description: Micronaut controllers, JWT authentication, permission guards and error mapping.
---

The `api/` module is a Micronaut 4 application that exposes the core services over HTTP. It
contains no business rules: a controller validates the shape of the request, builds a `Caller`
from the JWT principal, calls a core service and maps the `Result` to a response.

## Authentication (JWT)

Login and refresh are provided by Micronaut Security; the credentials are checked by the core
`AuthService` (BCrypt) through a custom [`AuthenticationProvider`](https://github.com/DevAldrete/sysport-marjan).

| Endpoint | Purpose |
| --- | --- |
| `POST /api/auth/login` | `{username,password}` → access + refresh token, roles = permissions |
| `POST /api/auth/refresh` | signed refresh token → new access token |
| `POST /api/auth/logout` | invalidate the session |
| `GET /api/auth/me` | current user id, username, role and permissions |
| `POST /api/auth/password` | change own password |

The user's **permission strings become the JWT roles**, so a controller is guarded with the
permission itself:

```java
@Controller("/api/requests")
public class RequestController {

    @Get("/{id}/packages")
    @Secured(Permissions.REQUESTS_READ)
    public List<CargoPackage> packages(Authentication authentication, long id) {
        return new CargoPackageService(Callers.forAuthentication(authentication)).list(id);
    }
}
```

`Callers.forAuthentication` adapts the Micronaut principal to the core [`Caller`](#the-caller-seam),
so the same service runs for any request without a global session.

## The Caller seam

`core` services never read a static session. They receive a `Caller`:

```java
public interface Caller {
    long userId();
    boolean has(String permission);
}
```

- In the API, `Callers.forAuthentication(authentication)` builds one from the request (roles from
  the token, user id from the claims).
- Scheduled jobs use `SystemCaller` (all permissions, configured user id).
- Identity-only lookups (login, token rehydration) use `Caller.NONE`.

## Result → HTTP

`Responses.of` maps a core `Result` to a response:

| Core | HTTP |
| --- | --- |
| `Result.Ok(value)` | `200` with the value (or `204` when null) |
| `Result.Err(problems)` | `422 {"problems":[…]}` |
| `DataException` | `500 {"problems":[…]}` (translated message) |
| missing `@Secured` grant | `403` |

## JSON conventions

- Enums travel as their **database value** (`in_transit`, `occasional`) via an api-side Jackson
  module that reflects `dbValue()` / `fromDb()`, keeping `core` free of Jackson.
- Dates are ISO strings (`LocalDate` → `yyyy-MM-dd`, `LocalDateTime` → ISO date-time); Jackson's
  dates-as-timestamps is disabled.
- Read models are the `v_*` views projected into the core records.

## Scheduled jobs

The desktop app ran a lifecycle sweep on a UI timer. It now runs server-side in `jobs/`:

- every 60 s: confirm scheduled dates and depart due trips (`sp_sweep_lifecycle`),
- hourly: recompute invoice paid/overdue (`sp_refresh_invoice_statuses`).

This keeps reads side-effect free. The audit user id is `sysport.jobs.system-user-id` (default 1).
