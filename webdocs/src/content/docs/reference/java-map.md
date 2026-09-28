---
title: Java class map
description: Every class by package — records, enums, repositories, services and views.
---

A quick index of `src/main/java/mx/marjan`. Each feature package follows the same shape:
`Thing` · `ThingRepository` · `ThingService` · `ThingView`.

## Root

| Class | Purpose |
| --- | --- |
| `App` | entry point (`Application`): connect, login, open the shell |

## `ui` (JavaFX toolkit + shell)

| Class | Purpose |
| --- | --- |
| `AppShell` | main window: sidebar + app bar + content; theme toggle, password change, logout |
| `Navigation` | permission-aware sidebar built from grouped items; collapsible |
| `ThemeManager` | Primer light/dark theme + app stylesheet, remembered between sessions |
| `Async` | `Task` wrapper: DB work off the FX thread |
| `Ui` | dialogs, confirms, buttons, toolbars, double-click helper |
| `BaseView` | screen shell: `load`, `loadRows`, `setStatus` |
| `FormPanel` | renders a `FormModel` into inputs with hints/live validation/computed fields |
| `ModalForm` | standard Guardar/Cancelar dialog |
| `RecordTable<T>` | the one generic table (columns as lambdas) |
| `RecordTablePanel<T>` | table + selection + action row |
| `StatusBadge`, `StatusTones` | colored status chips and their color mapping |
| `Icons` | Ikonli Feather icon factory |

## `shared`

| Class | Purpose |
| --- | --- |
| `Database` | connection + all `call*` helpers (including `inTransaction` for multi-row units of work) + `translate` + problem/error helpers |
| `Result` | sealed `Ok` / `Err` outcome type (a list of problems, split from the procedures' `'; '` string) |
| `DataException` | a JDBC failure already translated for the UI |
| `FormModel` | pure form state: values, live validation, computed fields (no toolkit) |
| `Money` | `BigDecimal` money format/parse |
| `Numbers` | decimal field parsing/formatting |
| `Dates` | `LocalDate`/`LocalDateTime` format/parse |
| `Text` | truncation and labels for table cells |
| `Validators` | username + live date/dateTime/money/number checks |

## `security`

| Class | Kind | Purpose |
| --- | --- | --- |
| `User`, `Role`, `CurrentUser` | records | identity and permissions |
| `UserStatus` | enum | `active`, `disabled` |
| `Permissions` | constants | the 33 permission strings |
| `Session` | static | current user; `has(permission)`, `userId()` |
| `UserRepository`, `AuditRepository` | repos | security procedures |
| `AuthService` | service | login, user admin, password changes (BR-23/24) |
| `LoginView`, `UsersView` | views | login dialog; admin CRUD |

## `clients`

| Class | Kind |
| --- | --- |
| `Client`, `ClientRate` | records |
| `ClientStatus`, `ClientType`, `PaymentTerms` | enums |
| `ClientRepository`, `ClientRateRepository` | repos |
| `ClientService` | service (clients + rates) |
| `ClientsView` | view |

## `routes`

`Route` (record) · `RouteRepository` · `RouteService` · `RoutesView`.

## `requests`

| Class | Kind |
| --- | --- |
| `ServiceRequest`, `RequestFilter`, `CargoPackage` | records |
| `RequestStatus`, `PackageUnit`, `PackageCondition` | enums |
| `ServiceRequestRepository`, `CargoPackageRepository` | repos |
| `ServiceRequestService` | service (lifecycle + packages) |
| `CargoPackageService` | service (list/replace packages, save receipts) |
| `ServiceRequestsView`, `ServiceRequestDetailDialog`, `PackageEditorPanel` | views |

## `trips`

| Class | Kind |
| --- | --- |
| `Trip`, `Delivery`, `Incident` | records |
| `TripStatus`, `DeliveryStatus`, `IncidentType` | enums |
| `TripRepository`, `DeliveryRepository`, `IncidentRepository` | repos |
| `TripService`, `DeliveryService`, `IncidentService` | services |
| `TripsView`, `TripDetailDialog` | views |

## `fleet`

| Class | Kind |
| --- | --- |
| `Vehicle`, `Maintenance`, `FuelLoad` | records |
| `VehicleStatus`, `MaintenanceType` | enums |
| `VehicleRepository`, `MaintenanceRepository`, `FuelLoadRepository` | repos |
| `VehicleService`, `MaintenanceService`, `FuelService` | services |
| `VehiclesView`, `FuelLoadsView` | views |

## `operators`

`Employee`, `License` (records) · `EmployeeStatus` (enum) · `EmployeeRepository` ·
`EmployeeService` · `OperatorsView`.

## `finance`

| Class | Kind |
| --- | --- |
| `Invoice`, `Payment`, `Expense`, `Advance`, `AdvanceBalance` | records |
| `InvoiceStatus`, `PaymentMethod`, `ExpenseType`, `AdvanceStatus` | enums |
| `InvoiceRepository`, `PaymentRepository`, `ExpenseRepository`, `AdvanceRepository` | repos |
| `InvoiceService`, `ExpenseService`, `AdvanceService` | services |
| `InvoicesView` | view (expenses/advances live in `TripDetailDialog`) |

## `reports`

| Class | Kind |
| --- | --- |
| `Report`, `DashboardAlerts` | records |
| `ReportRepository`, `DashboardRepository` | repos |
| `ReportService`, `DashboardService` | services |
| `ReportsView`, `DashboardView` | views |
| `CsvExporter` | CSV writer |

## Repository → procedure cheat sheet

| Repository method | Procedure |
| --- | --- |
| `ServiceRequestRepository.search` | `sp_requests_search` |
| `ServiceRequestRepository.authorize` | `sp_authorize_request` |
| `CargoPackageRepository.listByRequest` | `sp_request_packages` |
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
