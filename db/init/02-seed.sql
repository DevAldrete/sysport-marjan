-- SysPort - MARJAN :: seed data (development / demo)
-- All passwords are "admin123" (bcrypt, dev only). All data below is fake.
-- No AUTO_INCREMENT: ids are explicit here and the sequences table is primed
-- at the end so the application continues from the right numbers.

-- ---------------------------------------------------------------- security

INSERT INTO roles (id, name) VALUES
  (1, 'admin'),
  (2, 'traffic'),
  (3, 'maintenance'),
  (4, 'collections'),
  (5, 'viewer');

INSERT INTO permissions (id, name) VALUES
  (1, 'clients.read'), (2, 'clients.write'),
  (3, 'routes.read'), (4, 'routes.write'),
  (5, 'rates.read'), (6, 'rates.write'),
  (7, 'operators.read'), (8, 'operators.write'),
  (9, 'fleet.read'), (10, 'fleet.write'), (11, 'fleet.maintenance'),
  (12, 'fuel.read'), (13, 'fuel.write'),
  (14, 'requests.read'), (15, 'requests.write'), (16, 'requests.assign'),
  (17, 'trips.read'), (18, 'trips.write'), (19, 'trips.assign'),
  (20, 'expenses.read'), (21, 'expenses.write'),
  (22, 'advances.read'), (23, 'advances.write'),
  (24, 'deliveries.read'), (25, 'deliveries.write'),
  (26, 'incidents.read'), (27, 'incidents.write'),
  (28, 'invoices.read'), (29, 'invoices.write'),
  (30, 'payments.read'), (31, 'payments.write'),
  (32, 'reports.view'),
  (33, 'security.users');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions;
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
INSERT INTO role_permissions (role_id, permission_id)
SELECT 3, id FROM permissions
WHERE name IN ('fleet.read','fleet.write','fleet.maintenance','fuel.read','fuel.write',
               'reports.view','operators.read');
INSERT INTO role_permissions (role_id, permission_id)
SELECT 4, id FROM permissions
WHERE name IN ('invoices.read','invoices.write','payments.read','payments.write',
               'clients.read','requests.read','reports.view');
INSERT INTO role_permissions (role_id, permission_id)
SELECT 5, id FROM permissions
WHERE name LIKE '%.read' OR name = 'reports.view';

-- ---------------------------------------------------------------- people

INSERT INTO licenses (id, license_number, license_type, issue_date, expiration_date) VALUES
  (1, 'LIC-MRJ-0001', 'Federal C', '2022-03-01', '2027-03-01'),
  (2, 'LIC-MRJ-0002', 'Federal B', '2021-01-15', '2024-01-15'),
  (3, 'LIC-MRJ-0003', 'Federal C', '2023-06-10', '2026-12-10'),
  (4, 'LIC-MRJ-0004', 'Federal C', '2023-08-20', '2026-11-20'),
  (5, 'LIC-MRJ-0005', 'Federal B', '2024-02-01', '2028-05-05'),
  (6, 'LIC-MRJ-0006', 'Federal C', '2023-10-10', '2027-09-09');

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
   'OEDR750303JKL', 'OEDR750303HQRRZL04', 'Elena Diaz', '5551000094', 2, 'available'),
  (5, 'Miguel Angel Torres', 'Av. Juarez 55, Puebla', '5551000005', 'miguel.torres@marjan.mx',
   'TOMA820202MNO', 'TOMA820202HPLRRS05', 'Sara Torres', '5551000095', 5, 'resting'),
  (6, 'Fernando Castro Gil', 'Calle 20 de Noviembre 9, CDMX', '5551000006', 'fernando.castro@marjan.mx',
   'CAGF880808PQR', 'CAGF880808HDFRRS06', 'Luz Gil', '5551000096', 6, 'available');

INSERT INTO users (id, employee_id, username, password_hash, role_id, status) VALUES
  (1, NULL, 'admin',       '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 1, 'active'),
  (2, NULL, 'traffic',     '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 2, 'active'),
  (3, NULL, 'maint',       '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 3, 'active'),
  (4, NULL, 'collections', '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 4, 'active'),
  (5, NULL, 'viewer',      '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 5, 'active');

-- ---------------------------------------------------------------- clients

INSERT INTO clients
  (id, name, rfc, address, phone, email, contact_name, client_type, payment_terms,
   credit_limit, credit_days, status) VALUES
  (1, 'Distribuidora del Norte SA de CV', 'DNO950101AAA', 'Monterrey, NL', '8181000001',
   'compras@dnorte.mx', 'Laura Trevino', 'frequent', 'credit', 150000.00, 30, 'active'),
  (2, 'Alimentos del Bajio SA de CV', 'ABA020202BBB', 'Leon, GTO', '4771000002',
   'logistica@abajio.mx', 'Miguel Torres', 'occasional', 'cash', 0.00, 0, 'active'),
  (3, 'Muebles Modernos SA de CV', 'MMO180303CCC', 'Guadalajara, JAL', '3331000003',
   'trafico@mmuebles.mx', 'Sofia Ramirez', 'frequent', 'cash', 0.00, 0, 'active'),
  (4, 'Aceros del Pacifico SA de CV', 'APC050404DDD', 'Mazatlan, SIN', '6691000004',
   'compras@acerosp.mx', 'Diego Luna', 'occasional', 'credit', 80000.00, 15, 'active'),
  (5, 'Comercializadora del Golfo SA de CV', 'CGO100505EEE', 'Veracruz, VER', '2291000005',
   'trafico@cgolfo.mx', 'Paola Cruz', 'frequent', 'credit', 200000.00, 45, 'active'),
  (6, 'Servicios Logisticos del Centro SA de CV', 'SLC150606FFF', 'Queretaro, QRO', '4421000006',
   'operaciones@slcentro.mx', 'Ramon Vega', 'occasional', 'cash', 0.00, 0, 'active');

INSERT INTO routes (id, origin, destination, estimated_km, description) VALUES
  (1, 'CDMX', 'Monterrey, NL', 900.0, 'Corredor federal via Queretaro'),
  (2, 'CDMX', 'Guadalajara, JAL', 550.0, 'Ruta via Morelia'),
  (3, 'Monterrey, NL', 'Puebla, PUE', 1000.0, 'Retorno cargado'),
  (4, 'Leon, GTO', 'CDMX', 380.0, 'Tramo corto'),
  (5, 'CDMX', 'Merida, YUC', 1300.0, 'Ruta larga sureste'),
  (6, 'Guadalajara, JAL', 'Monterrey, NL', 750.0, 'Ruta centro-norte');

INSERT INTO client_rates (id, client_id, route_id, rate, valid_from, valid_to) VALUES
  (1, 1, 1, 42000.00, '2026-01-01', NULL),
  (2, 1, 3, 46000.00, '2026-01-01', NULL),
  (3, 1, 2, 30000.00, '2026-01-01', NULL),
  (4, 2, 4, 15000.00, '2026-01-01', NULL),
  (5, 3, 2, 28000.00, '2026-01-01', NULL),
  (6, 3, 4, 16000.00, '2026-01-01', NULL),
  (7, 4, 1, 45000.00, '2026-01-01', NULL),
  (8, 5, 5, 58000.00, '2026-01-01', NULL),
  (9, 5, 6, 39000.00, '2026-01-01', NULL),
  (10, 6, 2, 26000.00, '2026-01-01', NULL);

-- ---------------------------------------------------------------- fleet

INSERT INTO vehicles
  (id, internal_code, plates, brand, model, year, serial_number, vehicle_type,
   load_capacity, mileage, status) VALUES
  (1, 'ECO-01', 'ABC-123-A', 'Kenworth', 'T680', 2020, 'SN0001', 'Tractocamion', 35000.0, 210000.0, 'available'),
  (2, 'ECO-02', 'DEF-456-B', 'Freightliner', 'Cascadia', 2021, 'SN0002', 'Tractocamion', 36000.0, 185000.0, 'on_trip'),
  (3, 'ECO-03', 'GHI-789-C', 'Isuzu', 'ELF', 2018, 'SN0003', 'Camion 3.5t', 3500.0, 98000.0, 'maintenance'),
  (4, 'ECO-04', 'JKL-012-D', 'Volvo', 'VNL', 2019, 'SN0004', 'Tractocamion', 34000.0, 260000.0, 'out_of_service'),
  (5, 'ECO-05', 'MNO-345-E', 'International', 'LT', 2022, 'SN0005', 'Tractocamion', 37000.0, 60000.0, 'available'),
  (6, 'ECO-06', 'PQR-678-F', 'Kenworth', 'T800', 2017, 'SN0006', 'Tractocamion', 33000.0, 320000.0, 'available'),
  (7, 'ECO-07', 'STU-901-G', 'Ford', 'F-350', 2016, 'SN0007', 'Camioneta', 3000.0, 150000.0, 'decommissioned');

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
   15000.00, TRUE, 'cancelled', 'Cancelado por el cliente', 1),
  (7, 'SR-2026-000007', 4, 1, 'Laminas de acero', 20000.0, '2026-07-05 06:00:00', '2026-07-07 20:00:00',
   45000.00, TRUE, 'closed', NULL, 1),
  (8, 'SR-2026-000008', 5, 5, 'Electronica', 9000.0, '2026-09-10 05:30:00', '2026-09-13 22:00:00',
   58000.00, TRUE, 'delivered', 'Pendiente de facturar', 1),
  (9, 'SR-2026-000009', 5, 6, 'Envases de vidrio', 14000.0, '2026-09-28 07:00:00', '2026-09-30 19:00:00',
   39000.00, TRUE, 'scheduled', NULL, 1),
  (10, 'SR-2026-000010', 1, 2, 'Partes automotrices', 7000.0, '2026-10-01 06:00:00', '2026-10-03 18:00:00',
   30000.00, TRUE, 'assigned', NULL, 1),
  (11, 'SR-2026-000011', 6, 2, 'Papeleria', 2000.0, '2026-10-05 08:00:00', '2026-10-06 18:00:00',
   NULL, TRUE, 'requested', 'Solicitud nueva', 1),
  (12, 'SR-2026-000012', 4, 1, 'Perfiles de aluminio', 11000.0, NULL, NULL,
   45000.00, TRUE, 'authorized', 'Falta programar', 1),
  (13, 'SR-2026-000013', 3, 2, 'Salas y sillones', 12000.0, '2026-08-01 07:00:00', '2026-08-03 19:00:00',
   28000.00, TRUE, 'closed', NULL, 1),
  (14, 'SR-2026-000014', 6, 4, 'Herramienta', 1800.0, '2026-09-05 08:00:00', '2026-09-05 20:00:00',
   16000.00, TRUE, 'delivered', NULL, 1);

-- ---------------------------------------------------------------- trips

INSERT INTO trips
  (id, service_request_id, vehicle_id, employee_id, estimated_km, actual_km,
   planned_start, planned_end, departure_datetime, arrival_datetime, status, created_by) VALUES
  (1, 1, 1, 1, 900.0, 905.0, '2026-05-02 08:00:00', '2026-05-04 18:00:00',
   '2026-05-02 08:20:00', '2026-05-04 17:30:00', 'completed', 1),
  (2, 2, 5, 1, 550.0, 548.0, '2026-05-10 07:30:00', '2026-05-12 17:00:00',
   '2026-05-10 07:45:00', '2026-05-12 16:40:00', 'completed', 1),
  (3, 4, 2, 2, 1000.0, NULL, '2026-09-20 05:00:00', '2026-09-22 22:00:00',
   '2026-09-20 05:30:00', NULL, 'in_transit', 1),
  (4, 7, 6, 6, 900.0, 912.0, '2026-07-05 06:00:00', '2026-07-07 20:00:00',
   '2026-07-05 06:15:00', '2026-07-07 19:30:00', 'completed', 1),
  (5, 8, 5, 1, 1300.0, 1310.0, '2026-09-10 05:30:00', '2026-09-13 22:00:00',
   '2026-09-10 05:45:00', '2026-09-13 21:20:00', 'completed', 1),
  (6, 10, 1, 6, 550.0, NULL, '2026-10-01 06:00:00', '2026-10-03 18:00:00',
   NULL, NULL, 'scheduled', 1),
  (7, 13, 6, 5, 550.0, 553.0, '2026-08-01 07:00:00', '2026-08-03 19:00:00',
   '2026-08-01 07:10:00', '2026-08-03 18:40:00', 'completed', 1),
  (8, 14, 1, 6, 380.0, 378.0, '2026-09-05 08:00:00', '2026-09-05 20:00:00',
   '2026-09-05 08:10:00', '2026-09-05 19:50:00', 'completed', 1);

INSERT INTO deliveries (id, trip_id, actual_datetime, received_by, evidence_reference, status, created_by) VALUES
  (1, 1, '2026-05-04 17:30:00', 'Almacen Central', 'PO-88231', 'complete', 1),
  (2, 2, '2026-05-12 16:40:00', 'Sofia Ramirez', 'PO-88232', 'complete', 1),
  (3, 4, '2026-07-07 19:30:00', 'Diego Luna', 'PO-88233', 'complete', 1),
  (4, 5, '2026-09-13 21:20:00', 'Paola Cruz', 'PO-88234', 'complete', 1),
  (5, 7, '2026-08-03 18:40:00', 'Sofia Ramirez', 'PO-88235', 'complete', 1),
  (6, 8, '2026-09-05 19:50:00', 'Ramon Vega', 'PO-88236', 'complete', 1);

-- ---------------------------------------------------------------- costs

INSERT INTO expenses (id, trip_id, expense_type, amount, expense_date, description, created_by) VALUES
  (1, 1, 'tolls', 1850.00, '2026-05-02', 'Casetas CDMX-NL', 1),
  (2, 1, 'food', 640.00, '2026-05-03', 'Comidas operador', 1),
  (3, 1, 'lodging', 900.00, '2026-05-03', 'Hospedaje', 1),
  (4, 2, 'tolls', 1100.00, '2026-05-10', 'Casetas a Guadalajara', 1),
  (5, 3, 'tolls', 950.00, '2026-09-20', 'Casetas tramo norte', 1),
  (6, 3, 'repairs', 2200.00, '2026-09-21', 'Cambio de neumatico', 1),
  (7, 4, 'tolls', 1900.00, '2026-07-05', 'Casetas CDMX-Monterrey', 1),
  (8, 4, 'food', 520.00, '2026-07-06', 'Comidas', 1),
  (9, 5, 'tolls', 2400.00, '2026-09-10', 'Casetas a Merida', 1),
  (10, 5, 'lodging', 1400.00, '2026-09-11', 'Hospedaje', 1),
  (11, 5, 'food', 700.00, '2026-09-12', 'Comidas', 1),
  (12, 7, 'tolls', 1150.00, '2026-08-01', 'Casetas a Guadalajara', 1),
  (13, 8, 'parking', 180.00, '2026-09-05', 'Estacionamiento', 1),
  (14, 4, 'permits', 850.00, '2026-07-05', 'Permiso de transporte', 1),
  (15, 3, 'handling', 600.00, '2026-09-21', 'Maniobras de carga', 1);

INSERT INTO advances
  (id, trip_id, employee_id, amount_given, delivered_date, status, settled_at, settled_by, created_by) VALUES
  (1, 1, 1, 5000.00, '2026-05-02', 'settled', '2026-05-05 10:00:00', 1, 1),
  (2, 3, 2, 4000.00, '2026-09-20', 'pending', NULL, NULL, 1),
  (3, 4, 6, 3500.00, '2026-07-05', 'settled', '2026-07-08 09:00:00', 1, 1),
  (4, 5, 1, 6000.00, '2026-09-10', 'pending', NULL, NULL, 1),
  (5, 6, 6, 3000.00, '2026-10-01', 'pending', NULL, NULL, 1);

INSERT INTO fuel_loads
  (id, vehicle_id, trip_id, fuel_station, load_date, liters, price_per_liter, amount, odometer_reading, created_by) VALUES
  (1, 1, 1, 'Pemex Norte', '2026-05-02 09:00:00', 300.00, 24.500, 7350.00, 210100.0, 1),
  (2, 5, 2, 'Pemex Bajio', '2026-05-10 08:00:00', 180.00, 24.300, 4374.00, 60050.0, 1),
  (3, 2, 3, 'Pemex Saltillo', '2026-09-20 07:00:00', 250.00, 24.800, 6200.00, 185200.0, 1),
  (4, 6, 4, 'Pemex Queretaro', '2026-07-05 07:00:00', 280.00, 24.100, 6748.00, 320150.0, 1),
  (5, 5, 5, 'Pemex Oriente', '2026-09-10 06:30:00', 400.00, 24.400, 9760.00, 60200.0, 1),
  (6, 1, 8, 'Pemex Norte', '2026-09-05 07:30:00', 150.00, 24.600, 3690.00, 210220.0, 1),
  (7, 6, 7, 'Pemex Bajio', '2026-08-01 07:00:00', 170.00, 24.200, 4114.00, 320400.0, 1),
  (8, 2, NULL, 'Pemex Saltillo', '2026-09-19 18:00:00', 120.00, 24.700, 2964.00, 185050.0, 1),
  (9, 5, NULL, 'Pemex Oriente', '2026-09-08 18:00:00', 90.00, 24.400, 2196.00, 59900.0, 1),
  (10, 1, NULL, 'Pemex Norte', '2026-09-02 18:00:00', 100.00, 24.600, 2460.00, 210130.0, 1);

INSERT INTO maintenance
  (id, vehicle_id, maintenance_date, odometer_reading, maintenance_type, work_performed, provider, cost,
   next_service_date, next_service_km, created_by) VALUES
  (1, 3, '2026-09-01', 97500.0, 'corrective', 'Reparacion de transmision', 'Taller Central', 18500.00,
   '2026-12-01', 105000.0, 1),
  (2, 1, '2026-04-15', 208000.0, 'preventive', 'Servicio mayor', 'Taller Autorizado', 9500.00,
   '2026-10-15', 218000.0, 1),
  (3, 6, '2026-06-20', 318000.0, 'preventive', 'Cambio de balatas', 'Taller Central', 6200.00,
   '2026-09-20', 328000.0, 1),
  (4, 5, '2026-08-10', 59000.0, 'preventive', 'Afinacion menor', 'Taller Autorizado', 4800.00,
   '2026-11-10', 69000.0, 1),
  (5, 2, '2026-03-05', 180000.0, 'corrective', 'Reparacion de frenos', 'Taller Norte', 12000.00,
   '2026-09-05', 190000.0, 1);

INSERT INTO incidents
  (id, trip_id, incident_date, incident_time, location, incident_type, description, actions_taken, created_by) VALUES
  (1, 3, '2026-09-21', '11:30:00', 'Saltillo, Coahuila', 'mechanical_failure', 'Reventaron dos neumaticos',
   'Se reemplazaron en taller local', 1),
  (2, 1, '2026-05-03', '16:00:00', 'Matehuala, SLP', 'delay', 'Cierre carretero por accidente',
   'Se desvio por ruta alterna', 1),
  (3, 5, '2026-09-12', '09:15:00', 'Villahermosa, TAB', 'road_closure', 'Manifestacion bloquea la carretera',
   'Se espero 3 horas', 1),
  (4, 8, '2026-09-05', '13:40:00', 'San Juan del Rio, QRO', 'cargo_damage', 'Caja con herramienta danada',
   'Se documento con el cliente', 1);

-- ---------------------------------------------------------------- finance

INSERT INTO invoices
  (id, client_id, service_request_id, invoice_number, amount, issue_date, due_date, status, created_by) VALUES
  (1, 1, 1, 'INV-2026-0001', 42000.00, '2026-06-01', '2026-07-01', 'overdue', 1),
  (2, 3, 2, 'INV-2026-0002', 28000.00, '2026-05-15', '2026-05-15', 'paid', 1),
  (3, 4, 7, 'INV-2026-0003', 45000.00, '2026-08-01', '2026-08-16', 'pending', 1),
  (4, 5, 8, 'INV-2026-0004', 58000.00, '2026-09-15', '2026-10-30', 'pending', 1),
  (5, 3, 13, 'INV-2026-0005', 28000.00, '2026-08-20', '2026-08-20', 'paid', 1);

INSERT INTO payments (id, invoice_id, amount, payment_date, payment_method, created_by) VALUES
  (1, 2, 28000.00, '2026-05-15', 'cash', 1),
  (2, 3, 20000.00, '2026-08-10', 'transfer', 1),
  (3, 5, 28000.00, '2026-08-20', 'check', 1),
  (4, 4, 10000.00, '2026-09-20', 'transfer', 1);

-- ---------------------------------------------------------------- sequences
-- Prime ids from the data above so the app continues without collisions.

INSERT INTO sequences (name, next_value)
SELECT 'clients', COALESCE(MAX(id), 0) FROM clients
UNION ALL SELECT 'routes', COALESCE(MAX(id), 0) FROM routes
UNION ALL SELECT 'client_rates', COALESCE(MAX(id), 0) FROM client_rates
UNION ALL SELECT 'licenses', COALESCE(MAX(id), 0) FROM licenses
UNION ALL SELECT 'employees', COALESCE(MAX(id), 0) FROM employees
UNION ALL SELECT 'users', COALESCE(MAX(id), 0) FROM users
UNION ALL SELECT 'vehicles', COALESCE(MAX(id), 0) FROM vehicles
UNION ALL SELECT 'service_requests', COALESCE(MAX(id), 0) FROM service_requests
UNION ALL SELECT 'trips', COALESCE(MAX(id), 0) FROM trips
UNION ALL SELECT 'deliveries', COALESCE(MAX(id), 0) FROM deliveries
UNION ALL SELECT 'expenses', COALESCE(MAX(id), 0) FROM expenses
UNION ALL SELECT 'advances', COALESCE(MAX(id), 0) FROM advances
UNION ALL SELECT 'fuel_loads', COALESCE(MAX(id), 0) FROM fuel_loads
UNION ALL SELECT 'maintenance', COALESCE(MAX(id), 0) FROM maintenance
UNION ALL SELECT 'incidents', COALESCE(MAX(id), 0) FROM incidents
UNION ALL SELECT 'invoices', COALESCE(MAX(id), 0) FROM invoices
UNION ALL SELECT 'payments', COALESCE(MAX(id), 0) FROM payments
UNION ALL SELECT 'audit_log', COALESCE(MAX(id), 0) FROM audit_log;
