---
title: The layers
description: A vertical slice, file by file, from the record to the Vue view.
---

This page follows one feature — **service requests** — through every layer. Every feature in the
app has the same shape.

## 1. Record — `core/…/requests/ServiceRequest.java`

Immutable data, no I/O. A `label()` gives combo boxes and tables a compact one-liner.

```java
public record ServiceRequest(
        long id, String folio, long clientId, String clientName, long routeId, String routeLabel,
        String cargoDescription, BigDecimal estimatedWeight, int packageCount, BigDecimal packageWeight,
        LocalDateTime pickupScheduled, LocalDateTime deliveryScheduled, BigDecimal agreedRate,
        boolean requiresDocuments, RequestStatus status, String notes, LocalDateTime createdAt) {

    public BigDecimal effectiveWeight() { return packageWeight != null ? packageWeight : estimatedWeight; }
    public String label() { return folio + " - " + clientName; }
}
```

The status is an enum, but the enum is **only vocabulary**: the valid transitions (BR-03) live in
the database function `fn_request_can_transition`.

## 2. Repository — `core/…/requests/ServiceRequestRepository.java`

A thin wrapper. Each method is one stored-procedure call plus a `ResultSet` → record mapping.

```java
public Optional<ServiceRequest> findById(long id) {
    return Database.callOne("{call sp_request_by_id(?)}", this::map, id);
}

public Result<Void> authorize(long id, BigDecimal rate, long userId) {
    return Database.callVoid("{call sp_authorize_request(?,?,?,?)}", id, rate, userId);
}
```

Repositories contain **no decisions** — no permission checks, no business rules.

## 3. Service — `core/…/requests/ServiceRequestService.java`

One use case per method. The service checks the permission, delegates to the repository and adapts
the outcome.

```java
public Result<ServiceRequest> authorize(long id, BigDecimal rate) {
    if (!caller.has(Permissions.REQUESTS_WRITE)) {
        return Result.err("No tiene permiso para autorizar solicitudes");
    }
    return afterMove(requests.authorize(id, rate, caller.userId()), id);
}
```

The `caller` is injected; there is no static session.

## 4. Controller — `api/…/requests/RequestController.java`

Maps HTTP to the service, guards with the same permission and turns the `Result` into a status.

```java
@Post("/{id}/authorize")
@Secured(Permissions.REQUESTS_WRITE)
public HttpResponse<?> authorize(Authentication authentication, long id, @Body RateWrite body) {
    return Responses.of(service(authentication).authorize(id, body.rate()));
}
```

## 5. View — `frontend/src/views/RequestsView.vue`

The Vue screen: a filter bar, a table and action menus. It calls the typed API client and shows the
server's `problems` when a write is rejected. It never contains SQL or business rules.

```ts
async function submitAuthorize(values: Record<string, unknown>) {
  try {
    await requestsApi.authorize(actionTarget.value.id, Number(values.rate))
    await refresh()
  } catch (failure) {
    actionProblems.value = problemsOf(failure)
  }
}
```

## The full flow of one action

```text
user clicks "Autorizar"
  → RequestsView.submitAuthorize()
    → requestsApi.authorize(id, rate)  (POST /api/requests/{id}/authorize, bearer JWT)
      → RequestController.authorize()                @Secured(requests.write)
        → ServiceRequestService.authorize()
          → caller.has(REQUESTS_WRITE)               (permission)
          → ServiceRequestRepository.authorize()
            → Database.callVoid("{call sp_authorize_request(?,?,?,?)}", …)
              → MySQL: fn_request_can_transition + UPDATE + audit row
      ← 200 ServiceRequest  or  422 {problems:[…]}
  ← on the client: dialog closes, query cache invalidated, list reloads
```

## Rules for adding code

| You are writing… | Put the decision in… |
| --- | --- |
| A validation or calculation rule | a `fn_*` function or `sp_*` procedure |
| A read query | a `sp_*` procedure + a repository method |
| A permission check | the service method (and `@Secured` on the controller) |
| HTTP shape, status codes, DTOs | the controller |
| Layout, labels, dialogs | the Vue view/component |
| Pure formatting/parsing shared by views | `lib/` (frontend) or `shared/` (core) |

Next: [The API](/architecture/api/).
