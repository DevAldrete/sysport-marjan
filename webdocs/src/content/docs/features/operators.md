---
title: Operators
description: Employees, their licenses, and the expiry rules that gate assignment.
---

Package `mx.marjan.operators`. An **operator** is an employee who drives; every operator has (or
should have) a license.

## Employee + license

An `Employee` embeds a `License` (number, type, issue date, expiration date). `v_employee` left-joins
the license, so an employee without one still lists.

| Operation | Service | Permission | Notes |
| --- | --- | --- | --- |
| Search / find | `EmployeeService` | none | |
| Save | `EmployeeService.save` | `operators.write` | writes license + employee in one transaction |
| Change status | `EmployeeService.setStatus` | `operators.write` | manual statuses only |
| Delete | `EmployeeService.delete` | `operators.write` | blocked if trips/advances/user exist |

`sp_employee_save` validates name, phone, email, RFC, CURP, emergency phone and the license
(format + dates). The **licence type** comes from a controlled list (`LicenseType` mirroring the DB
`CHECK`) and the **internal number** is assigned by the database (`fn_next_license_number`,
`LIC-MRJ-####`) when blank, so it is never typed by hand (BR-25). A new employee starts
`available`. `sp_employee_delete` removes the employee and its license together.

## Status

Manual statuses (`EmployeeStatus.manualValues()`): `available`, `resting`, `vacation`,
`incapacitated`, `terminated`. `on_trip` is owned by the trip lifecycle; an operator on an active
trip is locked.

## License rules — BR-09 / BR-10

- An operator must have a license valid **through the trip's planned end** to be assigned.
- Licenses expiring within **30 days** are warnings (`sp_expiring_licenses`, dashboard).
- **Expired** licenses block assignment (`sp_validate_operator_assignment`).

The Operators table computes the expiry cell itself: `VENCIDA`, `Por vencer` (≤30 days), or the
date.

## Java map

| Layer | Class |
| --- | --- |
| Records | `Employee`, `License` |
| Enum | `EmployeeStatus`, `LicenseType` |
| Repository | `EmployeeRepository` |
| Service | `EmployeeService` |
| View | `OperatorsView` |

## Related

- Eligibility for assignment: `sp_eligible_operators_full` (see [Assignment](/domain/assignment/)).
- License alerts: `sp_expiring_licenses` and the [Dashboard](/features/reports/).

Next: [Finance](/features/finance/).
