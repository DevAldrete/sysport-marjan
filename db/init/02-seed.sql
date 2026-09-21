-- SysPort - MARJAN :: seed data
-- Development logins (password is the same for all): admin123
-- Change these before any real use. All data below is fake.

-- ---------------------------------------------------------------- security

INSERT INTO roles (id, name) VALUES
  (1, 'admin'),
  (2, 'traffic'),
  (3, 'maintenance'),
  (4, 'collections'),
  (5, 'viewer');

INSERT INTO permissions (name) VALUES
  ('clients.read'), ('clients.write'),
  ('routes.read'), ('routes.write'),
  ('rates.read'), ('rates.write'),
  ('operators.read'), ('operators.write'),
  ('fleet.read'), ('fleet.write'), ('fleet.maintenance'),
  ('fuel.read'), ('fuel.write'),
  ('requests.read'), ('requests.write'), ('requests.assign'),
  ('trips.read'), ('trips.write'), ('trips.assign'),
  ('expenses.read'), ('expenses.write'),
  ('advances.read'), ('advances.write'),
  ('deliveries.read'), ('deliveries.write'),
  ('incidents.read'), ('incidents.write'),
  ('invoices.read'), ('invoices.write'),
  ('payments.read'), ('payments.write'),
  ('reports.view'),
  ('security.users');

-- admin gets every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions;

-- traffic/dispatcher
INSERT INTO role_permissions (role_id, permission_id)
SELECT 2, id FROM permissions
WHERE name IN (
  'clients.read','clients.write','routes.read','routes.write','rates.read','rates.write',
  'operators.read','fleet.read','fuel.read','fuel.write',
  'requests.read','requests.write','requests.assign',
  'trips.read','trips.write','trips.assign',
  'expenses.read','expenses.write','advances.read','advances.write',
  'deliveries.read','deliveries.write','incidents.read','incidents.write',
  'payments.read','reports.view');

-- maintenance / fleet manager
INSERT INTO role_permissions (role_id, permission_id)
SELECT 3, id FROM permissions
WHERE name IN ('fleet.read','fleet.write','fleet.maintenance','fuel.read','fuel.write',
               'reports.view','operators.read');

-- collections / billing clerk
INSERT INTO role_permissions (role_id, permission_id)
SELECT 4, id FROM permissions
WHERE name IN ('invoices.read','invoices.write','payments.read','payments.write',
               'clients.read','requests.read','reports.view');

-- viewer: every *.read plus reports
INSERT INTO role_permissions (role_id, permission_id)
SELECT 5, id FROM permissions
WHERE name LIKE '%.read' OR name = 'reports.view';

-- ---------------------------------------------------------------- people

INSERT INTO licenses (id, license_number, license_type, issue_date, expiration_date) VALUES
  (1, 'LIC-MRJ-0001', 'Federal C', '2022-03-01', '2027-03-01'),
  (2, 'LIC-MRJ-0002', 'Federal B', '2021-01-15', '2024-01-15'),
  (3, 'LIC-MRJ-0003', 'Federal C', '2023-06-10', '2026-12-10'),
  (4, 'LIC-MRJ-0004', 'Federal C', '2023-08-20', '2026-11-20');

INSERT INTO employees
  (id, name, address, phone, email, rfc, curp,
   emergency_contact_name, emergency_contact_phone, license_id, status) VALUES
  (1, 'Jose Martinez Rios', 'Av. Reforma 120, CDMX', '5551000001', 'jose.martinez@marjan.mx',
   'MARI800101ABC', 'MARI800101HDFRSS01', 'Ana Rios', '5551000091', 1, 'available'),
  (2, 'Luis Hernandez Cano', 'Calle 5 de Mayo 40, Puebla', '5551000002', 'luis.hernandez@marjan.mx',
   'HECL850505DEF', 'HECL850505HPLRNN02', 'Maria Cano', '5551000092', 3, 'on_trip'),
  (3, 'Pedro Sanchez Lima', 'Blvd. Kukulcan 8, Cancun', '5551000003', 'pedro.sanchez@marjan.mx',
   'SALP900912GHI', 'SALP900912HQRNND03', 'Rosa Lima', '5551000093', 4, 'vacation'),
  (4, 'Raul Ortega Diaz', 'Calle Hidalgo 3, Queretaro', '5551000004', 'raul.ortega@marjan.mx',
   'OEDR750303JKL', 'OEDR750303HQRRZL04', 'Elena Diaz', '5551000094', 2, 'available');

-- all passwords are "admin123" (bcrypt, dev only)
INSERT INTO users (id, employee_id, username, password_hash, role_id, status) VALUES
  (1, NULL, 'admin',    '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 1, 'active'),
  (2, NULL, 'traffic',  '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 2, 'active'),
  (3, NULL, 'maint',    '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 3, 'active'),
  (4, NULL, 'collections', '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 4, 'active'),
  (5, NULL, 'viewer',   '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 5, 'active');

-- ---------------------------------------------------------------- clients

INSERT INTO clients
  (id, name, rfc, address, phone, email, contact_name, client_type, payment_terms,
   credit_limit, credit_days, status) VALUES
  (1, 'Distribuidora del Norte SA de CV', 'DNO950101AAA', 'Monterrey, NL', '8181000001',
   'compras@dnorte.mx', 'Laura Trevino', 'frequent', 'credit', 150000.00, 30, 'active'),
  (2, 'Alimentos del Bajio SA de CV', 'ABA020202BBB', 'Leon, GTO', '4771000002',
   'logistica@abajio.mx', 'Miguel Torres', 'occasional', 'cash', 0.00, 0, 'active'),
  (3, 'Muebles Modernos SA de CV', 'MMO180303CCC', 'Guadalajara, JAL', '3331000003',
   'trafico@mmuebles.mx', 'Sofia Ramirez', 'frequent', 'cash', 0.00, 0, 'active');

INSERT INTO routes (id, origin, destination, estimated_km, description) VALUES
  (1, 'CDMX', 'Monterrey, NL', 900.0, 'Corredor federal via Queretaro'),
  (2, 'CDMX', 'Guadalajara, JAL', 550.0, 'Ruta via Morelia'),
  (3, 'Monterrey, NL', 'Puebla, PUE', 1000.0, 'Retorno cargado'),
  (4, 'Leon, GTO', 'CDMX', 380.0, 'Tramo corto');

INSERT INTO client_rates (client_id, route_id, rate, valid_from, valid_to) VALUES
  (1, 1, 42000.00, '2026-01-01', NULL),
  (1, 3, 46000.00, '2026-01-01', NULL),
  (2, 4, 15000.00, '2026-01-01', NULL),
  (3, 2, 28000.00, '2026-01-01', NULL);

-- ---------------------------------------------------------------- fleet

INSERT INTO vehicles
  (id, internal_code, plates, brand, model, year, serial_number, vehicle_type,
   load_capacity, mileage, status) VALUES
  (1, 'ECO-01', 'ABC-123-A', 'Kenworth', 'T680', 2020, 'SN0001', 'Tractocamion', 35000.0, 210000.0, 'available'),
  (2, 'ECO-02', 'DEF-456-B', 'Freightliner', 'Cascadia', 2021, 'SN0002', 'Tractocamion', 36000.0, 185000.0, 'on_trip'),
  (3, 'ECO-03', 'GHI-789-C', 'Isuzu', 'ELF', 2018, 'SN0003', 'Camion 3.5t', 3500.0, 98000.0, 'maintenance'),
  (4, 'ECO-04', 'JKL-012-D', 'Volvo', 'VNL', 2019, 'SN0004', 'Tractocamion', 34000.0, 260000.0, 'out_of_service'),
  (5, 'ECO-05', 'MNO-345-E', 'International', 'LT', 2022, 'SN0005', 'Tractocamion', 37000.0, 60000.0, 'available');

-- ---------------------------------------------------------------- requests

INSERT INTO service_requests
  (id, folio, client_id, route_id, cargo_description, estimated_weight, pickup_date_scheduled,
   delivery_date_scheduled, agreed_rate, requires_documents, status, notes, created_by) VALUES
  (1, 'SR-2026-000001', 1, 1, 'Refrigeradores', 12000.0, '2026-05-02 08:00:00', '2026-05-04 18:00:00',
   42000.00, TRUE, 'closed', 'Entrega completa', 1),
  (2, 'SR-2026-000002', 3, 2, 'Muebles de oficina', 8000.0, '2026-05-10 07:30:00', '2026-05-12 17:00:00',
   28000.00, TRUE, 'closed', NULL, 1),
  (3, 'SR-2026-000003', 2, 4, 'Alimentos no perecederos', 3000.0, '2026-09-25 06:00:00', '2026-09-25 20:00:00',
   15000.00, TRUE, 'scheduled', 'Pendiente de asignacion', 1),
  (4, 'SR-2026-000004', 1, 3, 'Autopartes', 15000.0, '2026-09-20 05:00:00', '2026-09-22 22:00:00',
   46000.00, TRUE, 'in_transit', NULL, 1),
  (5, 'SR-2026-000005', 3, 2, 'Electrodomesticos', 5000.0, '2026-09-28 08:00:00', '2026-09-30 18:00:00',
   NULL, TRUE, 'requested', 'Falta autorizar', 1),
  (6, 'SR-2026-000006', 2, 4, 'Abarrotes', 2500.0, '2026-09-15 08:00:00', '2026-09-15 20:00:00',
   15000.00, TRUE, 'cancelled', 'Cancelado por el cliente', 1);

-- ---------------------------------------------------------------- trips

INSERT INTO trips
  (id, service_request_id, vehicle_id, employee_id, estimated_km, actual_km,
   planned_start, planned_end, departure_datetime, arrival_datetime, status, created_by) VALUES
  (1, 1, 1, 1, 900.0, 905.0, '2026-05-02 08:00:00', '2026-05-04 18:00:00',
   '2026-05-02 08:20:00', '2026-05-04 17:30:00', 'completed', 1),
  (2, 2, 5, 3, 550.0, 548.0, '2026-05-10 07:30:00', '2026-05-12 17:00:00',
   '2026-05-10 07:45:00', '2026-05-12 16:40:00', 'completed', 1),
  (3, 4, 2, 2, 1000.0, NULL, '2026-09-20 05:00:00', '2026-09-22 22:00:00',
   '2026-09-20 05:30:00', NULL, 'in_transit', 1);

INSERT INTO deliveries (trip_id, actual_datetime, received_by, evidence_reference, status, created_by) VALUES
  (1, '2026-05-04 17:30:00', 'Almacen Central', 'PO-88231', 'complete', 1),
  (2, '2026-05-12 16:40:00', 'Sofia Ramirez', 'PO-88232', 'complete', 1);

-- ---------------------------------------------------------------- costs

INSERT INTO expenses (trip_id, expense_type, amount, expense_date, description, created_by) VALUES
  (1, 'tolls', 1850.00, '2026-05-02', 'Casetas CDMX-NL', 1),
  (1, 'food', 640.00, '2026-05-03', 'Comidas operador', 1),
  (1, 'lodging', 900.00, '2026-05-03', 'Hospedaje', 1),
  (2, 'tolls', 1100.00, '2026-05-10', 'Casetas a Guadalajara', 1),
  (3, 'tolls', 950.00, '2026-09-20', 'Casetas tramo norte', 1),
  (3, 'repairs', 2200.00, '2026-09-21', 'Cambio de neumatico', 1);

INSERT INTO advances (trip_id, employee_id, amount_given, delivered_date, status, settled_at, settled_by, created_by) VALUES
  (1, 1, 5000.00, '2026-05-02', 'settled', '2026-05-05 10:00:00', 1, 1),
  (3, 2, 4000.00, '2026-09-20', 'pending', NULL, NULL, 1);

INSERT INTO fuel_loads
  (vehicle_id, trip_id, fuel_station, load_date, liters, price_per_liter, amount, odometer_reading, created_by) VALUES
  (1, 1, 'Pemex Norte', '2026-05-02 09:00:00', 300.00, 24.500, 7350.00, 210100.0, 1),
  (5, 2, 'Pemex Bajio', '2026-05-10 08:00:00', 180.00, 24.300, 4374.00, 60050.0, 1),
  (2, 3, 'Pemex Saltillo', '2026-09-20 07:00:00', 250.00, 24.800, 6200.00, 185200.0, 1);

INSERT INTO maintenance
  (vehicle_id, maintenance_date, odometer_reading, maintenance_type, work_performed, provider, cost, next_service_date, next_service_km, created_by) VALUES
  (3, '2026-09-01', 97500.0, 'corrective', 'Reparacion de transmision', 'Taller Central', 18500.00, '2026-12-01', 105000.0, 1),
  (1, '2026-04-15', 208000.0, 'preventive', 'Servicio mayor', 'Taller Autorizado', 9500.00, '2026-10-15', 218000.0, 1);

INSERT INTO incidents (trip_id, incident_date, incident_time, location, incident_type, description, actions_taken, created_by) VALUES
  (3, '2026-09-21', '11:30:00', 'Saltillo, Coahuila', 'mechanical_failure', 'Reventaron dos neumaticos', 'Se reemplazaron en taller local', 1);

-- ---------------------------------------------------------------- finance

INSERT INTO invoices
  (id, client_id, service_request_id, invoice_number, amount, issue_date, due_date, status, created_by) VALUES
  (1, 1, 1, 'INV-2026-0001', 42000.00, '2026-06-01', '2026-07-01', 'overdue', 1),
  (2, 3, 2, 'INV-2026-0002', 28000.00, '2026-05-15', '2026-05-15', 'paid', 1);

INSERT INTO payments (invoice_id, amount, payment_date, payment_method, created_by) VALUES
  (2, 28000.00, '2026-05-15', 'cash', 1);
