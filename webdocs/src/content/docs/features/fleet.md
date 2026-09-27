---
title: Fleet
description: Vehicles, maintenance and fuel loads.
---

Package `mx.marjan.fleet`. Owns the trucks, their service history and their fuel consumption.

## Vehicles

| Operation | Service | Permission | Notes |
| --- | --- | --- | --- |
| Search / find | `VehicleService` | none | |
| Save | `VehicleService.save` | `fleet.write` | edit never lowers mileage (BR-21) |
| Change status | `VehicleService.setStatus` | `fleet.write` | manual statuses only |
| Delete | `VehicleService.delete` | `fleet.write` | blocked if referenced |

New vehicles start `available`. Manual statuses (`VehicleStatus.manualValues()`): `available`,
`maintenance`, `out_of_service`, `decommissioned`. `assigned`/`on_trip` are owned by the trip
lifecycle and cannot be set by hand; a vehicle on an active trip is locked from status changes.

## Maintenance

| Operation | Service | Permission | Rule |
| --- | --- | --- | --- |
| List by vehicle | `MaintenanceService` | none | |
| Register | `MaintenanceService.register` | `fleet.maintenance` | BR-21 mileage |
| Delete | `MaintenanceService.delete` | `fleet.maintenance` | |

`sp_maintenance_save` records date, odometer, type (preventive/corrective), work, provider, cost and
the optional next-service date/km; it advances `vehicles.mileage` when the odometer is higher, in one
transaction. "Maintenance due" is computed by `sp_maintenance_due` (next date ≤ today **or** next km
≤ mileage).

## Fuel loads

| Operation | Service | Permission | Rule |
| --- | --- | --- | --- |
| List (all / by vehicle / by trip) | `FuelService` | none | |
| Register | `FuelService.register` | `fuel.write` | BR-18 |
| Delete | `FuelService.delete` | `fuel.write` | |

Validation is in `sp_validate_fuel_load` (see [Costs & advances](/domain/costs/#fuel)).

:::tip
`FuelLoadsView` computes **Importe** live (`litros × precio`) in a read-only field and prefills the
odometer from the selected unit's last mileage — the database rejects an amount mismatch, so typing
it by hand was error-prone.
:::

## The UI

- **Unidades** tab: table + Nuevo / Editar / Cambiar estado / **Mantenimiento** / Eliminar.
  The maintenance dialog lists records and lets you register/delete.
- **Combustible** tab: table + Nueva carga / Eliminar.

## Java map

| Layer | Class |
| --- | --- |
| Records | `Vehicle`, `Maintenance`, `FuelLoad` |
| Enums | `VehicleStatus`, `MaintenanceType` |
| Repositories | `VehicleRepository`, `MaintenanceRepository`, `FuelLoadRepository` |
| Services | `VehicleService`, `MaintenanceService`, `FuelService` |
| Views | `VehiclesView`, `FuelLoadsView` |

Next: [Operators](/features/operators/).
