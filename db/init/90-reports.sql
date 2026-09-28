-- ================================================================ routines
-- Read queries for reports and the dashboard.

USE sysportdb;

DELIMITER $$

-- ------------------------------------------------- read queries (result sets)

-- FR-RPT-1: revenue per client.
CREATE PROCEDURE sp_revenue_by_client(IN p_from DATE, IN p_to DATE)
p: BEGIN
  SELECT c.name AS cliente,
         COUNT(DISTINCT sr.id) AS solicitudes,
         COALESCE(SUM(sr.agreed_rate), 0) AS facturado,
         COALESCE(SUM(p.paid), 0) AS cobrado
  FROM clients c
  LEFT JOIN service_requests sr
         ON sr.client_id = c.id
        AND sr.pickup_date_scheduled >= p_from
        AND sr.pickup_date_scheduled < DATE_ADD(p_to, INTERVAL 1 DAY)
  LEFT JOIN (SELECT invoice_id, SUM(amount) AS paid FROM payments GROUP BY invoice_id) p
         ON p.invoice_id = (SELECT i.id FROM invoices i WHERE i.service_request_id = sr.id)
  GROUP BY c.id, c.name
  ORDER BY facturado DESC;
END$$

-- FR-RPT-2: route usage.
CREATE PROCEDURE sp_route_usage(IN p_from DATE, IN p_to DATE)
p: BEGIN
  SELECT COALESCE(fn_route_label(r.id), CONCAT(r.origin, ' -> ', r.destination)) AS ruta,
         COUNT(t.id) AS viajes,
         COALESCE(SUM(sr.agreed_rate), 0) AS ingresos
  FROM routes r
  LEFT JOIN service_requests sr
         ON sr.route_id = r.id
        AND sr.pickup_date_scheduled >= p_from
        AND sr.pickup_date_scheduled < DATE_ADD(p_to, INTERVAL 1 DAY)
  LEFT JOIN trips t ON t.service_request_id = sr.id
  GROUP BY r.id, r.origin, r.destination
  ORDER BY viajes DESC;
END$$

-- FR-RPT-3: trips per vehicle.
CREATE PROCEDURE sp_vehicle_usage(IN p_from DATE, IN p_to DATE)
p: BEGIN
  SELECT v.internal_code AS unidad, v.plates AS placas,
         COUNT(t.id) AS viajes,
         COALESCE(SUM(t.actual_km), 0) AS km
  FROM vehicles v
  LEFT JOIN trips t ON t.vehicle_id = v.id
         AND t.planned_start >= p_from
         AND t.planned_start < DATE_ADD(p_to, INTERVAL 1 DAY)
  GROUP BY v.id, v.internal_code, v.plates
  ORDER BY viajes DESC;
END$$

-- FR-RPT-5 / PRD 8.5: fuel efficiency (km per litre).
CREATE PROCEDURE sp_fuel_efficiency(IN p_from DATE, IN p_to DATE)
p: BEGIN
  SELECT v.internal_code AS unidad,
         COALESCE(MAX(f.odometer_reading) - MIN(f.odometer_reading), 0) AS km,
         COALESCE(SUM(f.liters), 0) AS litros,
         ROUND(COALESCE(MAX(f.odometer_reading) - MIN(f.odometer_reading), 0)
               / NULLIF(SUM(f.liters), 0), 2) AS km_por_litro
  FROM vehicles v
  JOIN fuel_loads f ON f.vehicle_id = v.id
         AND f.load_date >= p_from
         AND f.load_date < DATE_ADD(p_to, INTERVAL 1 DAY)
  GROUP BY v.id, v.internal_code
  ORDER BY km_por_litro DESC;
END$$

-- FR-RPT-4 / PRD 8.4: revenue - expenses - fuel per trip.
CREATE PROCEDURE sp_profitability(IN p_from DATE, IN p_to DATE)
p: BEGIN
  SELECT sr.folio, c.name AS cliente,
         sr.agreed_rate AS ingreso,
         COALESCE(e.total, 0) + COALESCE(f.total, 0) AS costo,
         sr.agreed_rate - COALESCE(e.total, 0) - COALESCE(f.total, 0) AS margen
  FROM service_requests sr
  JOIN clients c ON c.id = sr.client_id
  JOIN trips t ON t.service_request_id = sr.id
  LEFT JOIN (SELECT trip_id, SUM(amount) AS total FROM expenses GROUP BY trip_id) e
         ON e.trip_id = t.id
  LEFT JOIN (SELECT trip_id, SUM(amount) AS total FROM fuel_loads GROUP BY trip_id) f
         ON f.trip_id = t.id
  WHERE t.planned_start >= p_from
    AND t.planned_start < DATE_ADD(p_to, INTERVAL 1 DAY)
  ORDER BY margen DESC;
END$$

-- FR-RPT-6: outstanding balances.
CREATE PROCEDURE sp_receivables()
p: BEGIN
  SELECT c.name AS cliente, i.invoice_number AS factura,
         i.amount AS importe, COALESCE(p.paid, 0) AS pagado,
         i.amount - COALESCE(p.paid, 0) AS saldo, i.due_date AS vencimiento
  FROM invoices i
  JOIN clients c ON c.id = i.client_id
  LEFT JOIN (SELECT invoice_id, SUM(amount) AS paid FROM payments GROUP BY invoice_id) p
         ON p.invoice_id = i.id
  WHERE i.status <> 'cancelled' AND i.amount > COALESCE(p.paid, 0)
  ORDER BY i.due_date;
END$$

-- BR-10: licences expiring within 30 days.
CREATE PROCEDURE sp_expiring_licenses(IN p_today DATE)
p: BEGIN
  SELECT e.name AS operador, l.license_number AS licencia, l.expiration_date AS vence
  FROM employees e
  JOIN licenses l ON l.id = e.license_id
  WHERE l.expiration_date <= DATE_ADD(p_today, INTERVAL 30 DAY)
  ORDER BY l.expiration_date;
END$$

-- BR-21 follow-up: maintenance due by date or mileage.
CREATE PROCEDURE sp_maintenance_due(IN p_today DATE)
p: BEGIN
  SELECT v.internal_code AS unidad, v.mileage AS kilometraje,
         m.next_service_date AS proxima_fecha, m.next_service_km AS proximo_km
  FROM maintenance m
  JOIN vehicles v ON v.id = m.vehicle_id
  WHERE (m.next_service_date IS NOT NULL AND m.next_service_date <= p_today)
     OR (m.next_service_km IS NOT NULL AND m.next_service_km <= v.mileage)
  ORDER BY m.next_service_date;
END$$

-- ---------------------------------------------------------------- dashboard

CREATE PROCEDURE sp_dashboard(IN p_today DATE, OUT p_expiring_licenses INT,
    OUT p_overdue_invoices INT, OUT p_maintenance_due INT, OUT p_pending_assignments INT)
p: BEGIN
  SELECT COUNT(*) INTO p_expiring_licenses
  FROM employees e JOIN licenses l ON l.id = e.license_id
  WHERE l.expiration_date <= DATE_ADD(p_today, INTERVAL 30 DAY);

  SELECT COUNT(*) INTO p_overdue_invoices
  FROM invoices i
  WHERE i.status <> 'cancelled' AND i.due_date < p_today
    AND i.amount > COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0);

  SELECT COUNT(DISTINCT m.vehicle_id) INTO p_maintenance_due
  FROM maintenance m JOIN vehicles v ON v.id = m.vehicle_id
  WHERE (m.next_service_date IS NOT NULL AND m.next_service_date <= p_today)
     OR (m.next_service_km IS NOT NULL AND m.next_service_km <= v.mileage);

  SELECT COUNT(*) INTO p_pending_assignments
  FROM service_requests WHERE status = 'scheduled';
END$$

DELIMITER ;
