---
title: Security & users
description: Authentication, the session, the permission model and the admin screens.
---

Package `mx.marjan.security`. Authentication and authorization are intentionally small.

## The pieces

| Class | Role |
| --- | --- |
| `AuthService` | login, user administration, password changes |
| `UserRepository` | JDBC over the security procedures |
| `CurrentUser` | the logged-in identity + its permission set |
| `Session` | static holder for the current user |
| `Permissions` | the 33 permission-string constants |
| `LoginView` | modal login dialog |
| `UsersView` | admin CRUD for users |
| `AuditRepository` | writes `audit_log` rows |

## Login flow — BR-23 / BR-24

```text
LoginView.prompt()
  → AuthService.login(username, password)
      → UserRepository.findByUsername
      → reject if disabled
      → BCrypt.checkpw(password, hash)
      → load permissions: sp_role_permissions(role_id)
  ← CurrentUser(id, username, roleName, permissions)
App.start → Session.login(user) → new MainFrame()
```

- Passwords are stored **only** as BCrypt hashes (cost 12), never logged (BR-24).
- A `disabled` user cannot log in (BR-23).
- Login failures return the same generic message for unknown user and wrong password:
  *"Usuario o contrasena incorrectos"*.

## The permission model

`CurrentUser.can(permission)` checks the set loaded at login. Two places use it:

1. **The UI** hides what you cannot do — `MainFrame` adds a tab only if
   `Session.has(permission)`; buttons are shown per screen.
2. **The services** check again, so the rules do not depend on the UI (BR-23).

```java
// MainFrame.buildTabs()
addTab(tabs, Permissions.CLIENTS_READ, "Clientes", ClientsView::new);
// addTab only adds the tab when Session.has(permission)
```

```java
// ServiceRequestService
if (!Session.has(Permissions.REQUESTS_WRITE)) {
    return Result.err("No tiene permiso para autorizar solicitudes");
}
```

:::tip
Permission checks are deliberately one line each and **not** hidden behind a helper. Seeing
`Session.has(Permissions.X)` at the top of a use case is the point.
:::

## Tabs and their permission

| Tab | View | Permission |
| --- | --- | --- |
| Inicio | `DashboardView` | none (always shown) |
| Clientes | `ClientsView` | `clients.read` |
| Rutas | `RoutesView` | `routes.read` |
| Operadores | `OperatorsView` | `operators.read` |
| Unidades | `VehiclesView` | `fleet.read` |
| Combustible | `FuelLoadsView` | `fuel.read` |
| Solicitudes | `ServiceRequestsView` | `requests.read` |
| Viajes | `TripsView` | `trips.read` |
| Facturas | `InvoicesView` | `invoices.read` |
| Reportes | `ReportsView` | `reports.view` |
| Usuarios | `UsersView` | `security.users` |

## User administration

`UsersView` (permission `security.users`) supports create, edit, reset password and delete. Rules:

- username `^[A-Za-z0-9._-]{3,50}$` and unique;
- password at least 6 characters;
- you cannot delete your own user;
- reset password uses a masked field.

The session menu (*Sesion*) offers **Cambiar contrasena** (own account, verifies the current
password), **Cerrar sesion** (re-runs `App.start`) and **Salir**.

## Audit trail — BR-22

`AuditRepository.log(entity, entityId, action, details)` writes to `audit_log`. Key lifecycle
procedures also write audit rows directly (`authorized`, `assigned`, `cancelled`, `reassigned`,
`departed`). Audit rows are **never** deleted, even by a request cascade delete.

## Tables

`roles`, `permissions`, `role_permissions`, `users`, `employees`, `audit_log` — see
[Schema](/database/schema/). The seed roles and permissions are in
[Seed data & roles](/database/seed/).

Next: [Clients & rates](/features/clients/).
