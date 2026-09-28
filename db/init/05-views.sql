-- ================================================================ views
-- Read projections used by several procedures. Keeping them here removes the
-- repeated 15-column SELECTs from the search/by-id routines, so a column
-- change happens in one place.

USE sysportdb;

CREATE OR REPLACE VIEW v_client AS
SELECT id, name, rfc, address, phone, email, contact_name, client_type,
       payment_terms, credit_limit, credit_days, status
FROM clients;

CREATE OR REPLACE VIEW v_route AS
SELECT id, origin, destination, estimated_km, description,
       COALESCE(fn_route_label(id), CONCAT(origin, ' -> ', destination)) AS route_label
FROM routes;

CREATE OR REPLACE VIEW v_vehicle AS
SELECT id, internal_code, plates, brand, model, year, serial_number, vehicle_type,
       load_capacity, mileage, status
FROM vehicles;

CREATE OR REPLACE VIEW v_employee AS
SELECT e.id, e.name, e.address, e.phone, e.email, e.rfc, e.curp,
       e.emergency_contact_name, e.emergency_contact_phone, e.status,
       l.id AS license_id, l.license_number, l.license_type, l.issue_date, l.expiration_date
FROM employees e
LEFT JOIN licenses l ON l.id = e.license_id;

CREATE OR REPLACE VIEW v_service_request AS
SELECT sr.id, sr.folio, sr.client_id, c.name AS client_name, sr.route_id,
       COALESCE(fn_route_label(sr.route_id), CONCAT(r.origin, ' -> ', r.destination)) AS route_label,
       sr.cargo_description, sr.estimated_weight,
       (SELECT COUNT(*) FROM request_packages p WHERE p.service_request_id = sr.id) AS package_count,
       (SELECT SUM(p.quantity * p.unit_weight) FROM request_packages p
         WHERE p.service_request_id = sr.id) AS package_weight,
       sr.pickup_date_scheduled,
       sr.delivery_date_scheduled, sr.agreed_rate, sr.requires_documents,
       sr.status, sr.notes, sr.created_at
FROM service_requests sr
JOIN clients c ON c.id = sr.client_id
JOIN routes r ON r.id = sr.route_id;

CREATE OR REPLACE VIEW v_trip AS
SELECT t.id, t.service_request_id, sr.folio, c.name AS client_name,
       COALESCE(fn_route_label(sr.route_id), CONCAT(r.origin, ' -> ', r.destination)) AS route_label,
       t.vehicle_id, CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label,
       t.employee_id, e.name AS employee_name,
       t.estimated_km, t.actual_km, t.planned_start, t.planned_end,
       t.departure_datetime, t.arrival_datetime, t.status
FROM trips t
JOIN service_requests sr ON sr.id = t.service_request_id
JOIN clients c ON c.id = sr.client_id
JOIN routes r ON r.id = sr.route_id
JOIN vehicles v ON v.id = t.vehicle_id
JOIN employees e ON e.id = t.employee_id;

CREATE OR REPLACE VIEW v_fuel_load AS
SELECT f.id, f.vehicle_id, f.trip_id, f.fuel_station, f.load_date, f.liters,
       f.price_per_liter, f.amount, f.odometer_reading,
       CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label, sr.folio
FROM fuel_loads f
JOIN vehicles v ON v.id = f.vehicle_id
LEFT JOIN trips t ON t.id = f.trip_id
LEFT JOIN service_requests sr ON sr.id = t.service_request_id;

CREATE OR REPLACE VIEW v_invoice AS
SELECT i.id, i.client_id, c.name AS client_name, i.service_request_id, sr.folio,
       i.invoice_number, i.amount, i.issue_date, i.due_date, i.status,
       COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0) AS paid
FROM invoices i
JOIN clients c ON c.id = i.client_id
JOIN service_requests sr ON sr.id = i.service_request_id;
