---
title: Reports
description: The eight reports, CSV export, and the alert dashboard.
---

Package `mx.marjan.reports`. Reports are plain SQL result sets rendered into a generic `Report`
record; the dashboard shows alert counters.

## The eight reports

| `Kind` | Title | Procedure | FR |
| --- | --- | --- | --- |
| `REVENUE` | Ingresos por cliente | `sp_revenue_by_client(from,to)` | FR-RPT-1 |
| `ROUTES` | Rutas mas utilizadas | `sp_route_usage(from,to)` | FR-RPT-2 |
| `VEHICLES` | Viajes por unidad | `sp_vehicle_usage(from,to)` | FR-RPT-3 |
| `FUEL` | Rendimiento de combustible | `sp_fuel_efficiency(from,to)` | FR-RPT-3 |
| `PROFITABILITY` | Rentabilidad por viaje | `sp_profitability(from,to)` | FR-RPT-4 |
| `RECEIVABLES` | Saldos por cobrar | `sp_receivables()` | FR-RPT-6 |
| `LICENSES` | Licencias por vencer | `sp_expiring_licenses(today)` | BR-10 |
| `MAINTENANCE` | Mantenimiento proximo | `sp_maintenance_due(today)` | BR-21 |

All report methods require `reports.view` (`ReportService`). Denial returns
*"No tiene permiso para ver reportes"*.

## How a report flows

```text
ReportsView.generate()
  → ReportService.<method>(from, to)          (permission checked)
    → ReportRepository.<method>()
      → Database.callReport(reader, "{call sp_...}", …)
        → reads ResultSetMetaData for headers, getObject for rows
  ← Result<Report>
  → Ui.style(DefaultTableModel) shows it
```

`Report` is a simple record: `title`, `headers`, `rows`. Headers come from the result-set column
labels, so **the procedure's `AS` aliases are the column titles** (in Spanish).

## CSV export — FR-RPT-7

`CsvExporter.write(report, file)` writes a header line and the rows, quoting any field containing a
comma, quote or newline. The screen uses a `JFileChooser` and names the file
`title.replace(' ', '_') + ".csv"`.

## Dashboard — FR-DSH-1

`DashboardView` shows four counters from `sp_dashboard(today, OUT×4)`:

| Card | Meaning |
| --- | --- |
| Licencias por vencer | licenses expiring within 30 days |
| Facturas vencidas | overdue unpaid invoices |
| Unidades con mantenimiento proximo | vehicles due for maintenance |
| Solicitudes por asignar | requests in `scheduled` |

It also greets the logged-in user (name + role) and has an **Actualizar** button. The dashboard tab
is always shown (no permission required), but the data it surfaces is aggregate.

## Profitability (the important one)

`sp_profitability` computes, per trip:

```text
margin = agreed_rate − (Σ expenses + Σ fuel)
```

Expenses and fuel are aggregated in **subqueries** before joining, avoiding the classic
join-multiplication bug (summing a one-to-many join inflates totals).

## Java map

| Layer | Class |
| --- | --- |
| Records | `Report`, `DashboardAlerts` |
| Repository | `ReportRepository`, `DashboardRepository` |
| Service | `ReportService`, `DashboardService` |
| View | `ReportsView`, `DashboardView` |
| Export | `CsvExporter` |

Next: [Java class map](/reference/java-map/).
