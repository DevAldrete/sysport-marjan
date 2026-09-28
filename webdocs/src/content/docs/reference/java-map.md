---
title: Code map
description: Every module, package and kind of class — backend and frontend.
---

The repository has three source trees: `core` (Java domain), `api` (Micronaut HTTP) and
`frontend` (Vue). Each feature slice has the same shape: `Thing` · `ThingRepository` · `ThingService`
· `ThingController` · a Vue view.

## `core` (`mx.marjan`)

### `shared`

| Class | Purpose |
| --- | --- |
| `Database` | connection pool access + all `call*` helpers (including `inTransaction`) + `translate` |
| `Result` | sealed `Ok` / `Err` outcome type (a list of problems) |
| `DataException` | a JDBC failure already translated for the user |
| `Money`, `Numbers`, `Dates`, `Text` | formatting/parsing helpers |
| `Validators` | username + live date/dateTime/money/number checks |

### `security`

| Class | Kind | Purpose |
| --- | --- | --- |
| `Caller` | interface | the acting user: `userId()`, `has(permission)`, plus `Caller.NONE` |
| `Permissions` | constants | the 33 permission strings |
| `CurrentUser`, `User`, `Role` | records | identity and permissions |
| `UserStatus` | enum | `active`, `disabled` |
| `AuthService` | service | login, `byUsername`, user admin, password changes (BR-23/24) |
| `UserRepository` | repo | security procedures |

### Feature packages

| Package | Classes |
| --- | --- |
| `clients` | `Client`, `ClientRate`, enums, `ClientRepository`, `ClientRateRepository`, `ClientService` |
| `routes` | `Route`, `RouteRepository`, `RouteService` |
| `requests` | `ServiceRequest`, `CargoPackage`, `RequestFilter`, enums, `ServiceRequestRepository`, `CargoPackageRepository`, `ServiceRequestService`, `CargoPackageService` |
| `trips` | `Trip`, `Delivery`, `Incident`, enums, repositories, `TripService`, `DeliveryService`, `IncidentService` |
| `fleet` | `Vehicle`, `Maintenance`, `FuelLoad`, `VehicleStatus`, `MaintenanceType`, repositories, `VehicleService`, `MaintenanceService`, `FuelService` |
| `operators` | `Employee`, `License`, `EmployeeStatus`, `EmployeeRepository`, `EmployeeService` |
| `finance` | `Expense`, `Advance`, `AdvanceBalance`, `Invoice`, `Payment`, enums, repositories, `ExpenseService`, `AdvanceService`, `InvoiceService` |
| `reports` | `Report`, `DashboardAlerts`, `ReportRepository`, `DashboardRepository`, `ReportService`, `DashboardService`, `CsvExporter` |

## `api` (`mx.marjan.api`)

| Package | Classes |
| --- | --- |
| root | `Application`, `HealthController` |
| `db` | `DataSourceFactory`, `DatabasePool` |
| `security` | `SysportAuthenticationProvider`, `Callers`, `SystemCaller`, `InMemoryRefreshTokenPersistence` |
| `auth` | `AuthController` |
| `clients` | `ClientController` |
| `routes` | `RouteController` |
| `operators` | `OperatorController` |
| `fleet` | `VehicleController`, `FuelController` |
| `requests` | `RequestController` |
| `trips` | `TripController`, `IncidentController`, `DeliveryController` |
| `finance` | `ExpenseController`, `AdvanceController`, `InvoiceController` |
| `reports` | `ReportController` |
| `dashboard` | `DashboardController` |
| `users` | `UserController` |
| `json` | `EnumDbModule` (enum ↔ dbValue) |
| `error` | `ApiProblemException`, `ApiProblemExceptionHandler`, `DataExceptionHandler` |
| `http` | `Responses` (`Result` → HTTP) |
| `jobs` | `LifecycleJobs` |

## `frontend/src`

| Path | Purpose |
| --- | --- |
| `api/client.ts` | ofetch client: bearer token, one-shot refresh on 401, text/blob support |
| `api/*.ts` | typed clients per feature |
| `stores/auth.ts` | Pinia session (access token in memory, refresh persisted) |
| `router/index.ts` | routes built from `features.ts` + guards |
| `features.ts` | the single list of screens (path, title, icon, permission, lazy component) |
| `layouts/AppShell.vue` | sidebar + topbar + animated `RouterView` |
| `views/*.vue` | one screen per route |
| `components/FormDialog.vue` | generic validated form dialog |
| `components/ConfirmDialog.vue`, `PageHeader.vue` | shared chrome |
| `components/*Dialog.vue` | domain dialogs (assignment, trip detail, request detail, package editor) |
| `components/ui/**` | shadcn-vue (Reka UI) primitives |
| `lib/enums.ts`, `lib/format.ts`, `lib/utils.ts` | vocabulary, formatting, `cn()` |

## Repository → procedure cheat sheet

| Repository method | Procedure |
| --- | --- |
| `ServiceRequestRepository.search` | `sp_requests_search` |
| `ServiceRequestRepository.authorize` | `sp_authorize_request` |
| `CargoPackageRepository.replace` | `sp_package_save` / `sp_package_delete` (one `inTransaction`) |
| `CargoPackageRepository.saveReceipts` | `sp_package_receipt_save` |
| `TripRepository.assign` | `sp_assign_trip` |
| `TripRepository.arrive` | `sp_arrive_trip` |
| `InvoiceRepository.createFromRequest` | `sp_create_invoice_from_request` |
| `InvoiceRepository.registerPayment` | `sp_register_payment` |
| `AdvanceRepository.balance` | `sp_advance_balance` |
| `FuelLoadRepository.save` | `sp_fuel_save` |
| `UserRepository.permissionsForRole` | `sp_role_permissions` |

The complete procedure list is in [Stored procedures](/database/procedures/).

Next: [Glossary](/reference/glossary/).
