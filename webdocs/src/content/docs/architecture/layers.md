---
title: The four layers
description: A vertical slice, file by file, from the record to the Swing view.
---

This page follows one feature — **service requests** — through all four layers. Every feature in the
app has the same shape.

## 1. Record — `requests/ServiceRequest.java`

Immutable data, no I/O. A `label()` gives combo boxes and tables a compact one-liner.

```java
public record ServiceRequest(
        long id, String folio, long clientId, String clientName, long routeId, String routeLabel,
        String cargoDescription, BigDecimal estimatedWeight, LocalDateTime pickupScheduled,
        LocalDateTime deliveryScheduled, BigDecimal agreedRate, boolean requiresDocuments,
        RequestStatus status, String notes, LocalDateTime createdAt) {

    public String label() { return folio + " - " + clientName; }

    @Override public String toString() { return label(); }
}
```

The status is an enum, but the enum is **only vocabulary**:

```java
public enum RequestStatus {
    REQUESTED("requested", "Solicitada"), /* … */ CANCELLED("cancelled", "Cancelada");
    // fromDb(String), dbValue(), label()
}
```

:::note
The valid transitions (BR-03) are **not** in the enum. They live in the database function
`fn_request_can_transition`, so the rule and the data that uses it stay together.
:::

## 2. Repository — `requests/ServiceRequestRepository.java`

A thin wrapper. Each method is one stored-procedure call plus a `ResultSet` → record mapping.

```java
public Optional<ServiceRequest> findById(long id) {
    return Database.callOne("{call sp_request_by_id(?)}", this::map, id);
}

public Result<Void> authorize(long id, BigDecimal rate, long userId) {
    return Database.callVoid("{call sp_authorize_request(?,?,?,?)}", id, rate, userId);
}
```

The `map(...)` method translates DB strings into enums:

```java
RequestStatus.fromDb(rs.getString("status")),
```

Repositories contain **no decisions** — no `if (status == …)`, no permission checks. They only move
data across the JDBC boundary.

## 3. Service — `requests/ServiceRequestService.java`

One use case per method. The service:

1. checks the permission,
2. delegates to the repository,
3. adapts the outcome (often re-reading the updated record).

```java
public Result<ServiceRequest> authorize(long id, BigDecimal rate) {
    if (!Session.has(Permissions.REQUESTS_WRITE)) {
        return Result.err("No tiene permiso para autorizar solicitudes");
    }
    return afterMove(requests.authorize(id, rate, Session.userId()), id);
}

private Result<ServiceRequest> afterMove(Result<Void> moved, long id) {
    if (moved.isErr()) {
        return Result.err(moved.problems());
    }
    return requests.findById(id).map(Result::ok).orElse(Result.err("Solicitud no encontrada"));
}
```

:::tip
Permission checks are deliberately one readable line. Do **not** hide them behind a helper — the
value is that a reader sees exactly which permission guards which operation.
:::

## 4. View — `requests/ServiceRequestsView.java`

The view extends `BaseView`, builds a filter bar + table + action buttons, and calls services on a
background thread. It never runs SQL.

```java
private void openAuthorize() {
    ServiceRequest request = requireSelection();
    if (request == null) return;
    Async.run(
            () -> request.agreedRate() != null ? request.agreedRate()
                    : clientService.suggestRate(request.clientId(), request.routeId(), Dates.today())
                            .orElse(BigDecimal.ZERO),
            suggested -> {
                FormPanel form = new FormPanel()
                        .addText("rate", "Tarifa acordada", suggested.toPlainString(),
                                "Importe sin IVA; se propone la tarifa del cliente")
                        .validate("rate", Validators.money());
                ModalForm.show(this, "Autorizar " + request.folio(), form, () -> {
                    Result<BigDecimal> rateResult = Money.require(form.text("rate"), "tarifa acordada");
                    return rateResult.isErr() ? rateResult
                            : service.authorize(request.id(), rateResult.value());
                }, this::reload);
            },
            failure -> Ui.failure(this, failure));
}
```

Notice the recurring shape: **load data → show a `FormPanel` in a `ModalForm` → on submit call the
service → on success `reload()`**.

## The full flow of one action

```text
user clicks "Autorizar"
  → ServiceRequestsView.openAuthorize()
    → Async.run(...)                       (off the EDT)
      → ServiceRequestService.authorize()
        → Session.has(REQUESTS_WRITE)      (permission)
        → ServiceRequestRepository.authorize()
          → Database.callVoid("{call sp_authorize_request(?,?,?,?)}", ...)
            → MySQL: fn_request_can_transition + UPDATE + audit row
      ← Result.ok(...) or Result.err("Solicitud no encontrada")
    ← on the EDT: ModalForm closes, view.reload()
```

## Rules for adding code

| You are writing… | Put the decision in… |
| --- | --- |
| A validation or calculation rule | a `fn_*` function or `sp_*` procedure |
| A read query | a `sp_*` procedure + a repository method |
| A permission check | the service method |
| Layout, labels, dialogs | the view |
| Pure formatting/parsing shared by views | `shared/` (e.g. `Money`, `Dates`) |

Next: [Database access](/architecture/database-access/).
