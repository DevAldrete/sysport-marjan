-- SysPort - MARJAN :: bootstrap script (schema + minimal seed)
-- MariaDB 11. English status codes, DECIMAL money. No AUTO_INCREMENT: ids are
-- allocated by the application through the `sequences` table.
-- The child column always references the parent id (PRD 4.1 F1).
-- FK constraints are declared inline so table creation order is self-documenting.
--
-- This file is the reference / manual bootstrap. Docker loads
-- db/init/01-schema.sql and db/init/02-seed.sql automatically.
-- Run manually with:  mysql -u root -p < SYSPORT_MARJAN.sql

CREATE DATABASE IF NOT EXISTS sysportdb
  CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE sysportdb;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------- security

CREATE TABLE roles (
  id   BIGINT PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE permissions (
  id   BIGINT PRIMARY KEY,
  name VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE role_permissions (
  role_id       BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, permission_id),
  CONSTRAINT fk_rp_role       FOREIGN KEY (role_id)       REFERENCES roles (id),
  CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- people

CREATE TABLE licenses (
  id              BIGINT PRIMARY KEY,
  license_number  VARCHAR(50)  NOT NULL UNIQUE,
  license_type    VARCHAR(50)  NOT NULL,
  issue_date      DATE,
  expiration_date DATE         NOT NULL,
  created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_licenses_expiration (expiration_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE employees (
  id                     BIGINT PRIMARY KEY,
  name                   VARCHAR(150) NOT NULL,
  address                VARCHAR(255),
  phone                  VARCHAR(30)  NOT NULL UNIQUE,
  email                  VARCHAR(150) UNIQUE,
  rfc                    VARCHAR(13)  UNIQUE,
  curp                   VARCHAR(18)  UNIQUE,
  emergency_contact_name  VARCHAR(150),
  emergency_contact_phone VARCHAR(30),
  license_id             BIGINT UNIQUE,
  status                 VARCHAR(20) NOT NULL DEFAULT 'available'
                         CHECK (status IN ('available','on_trip','resting','vacation','incapacitated','terminated')),
  created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_employees_license FOREIGN KEY (license_id) REFERENCES licenses (id),
  INDEX idx_employees_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- users reference the person they belong to (employee_id -> employees.id)
CREATE TABLE users (
  id            BIGINT PRIMARY KEY,
  employee_id   BIGINT UNIQUE,
  username      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(100) NOT NULL,
  role_id       BIGINT       NOT NULL,
  status        VARCHAR(20)  NOT NULL DEFAULT 'active'
                CHECK (status IN ('active','disabled')),
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
  CONSTRAINT fk_users_role     FOREIGN KEY (role_id)     REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- fleet

CREATE TABLE vehicles (
  id            BIGINT PRIMARY KEY,
  internal_code VARCHAR(30)  NOT NULL UNIQUE,
  plates        VARCHAR(20)  NOT NULL UNIQUE,
  brand         VARCHAR(50),
  model         VARCHAR(50),
  year          INT,
  serial_number VARCHAR(60)  UNIQUE,
  vehicle_type  VARCHAR(50),
  load_capacity DECIMAL(10,1),
  mileage       DECIMAL(10,1) NOT NULL DEFAULT 0,
  status        VARCHAR(20)  NOT NULL DEFAULT 'available'
                CHECK (status IN ('available','assigned','on_trip','maintenance','out_of_service','decommissioned')),
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_vehicles_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- clients

CREATE TABLE clients (
  id           BIGINT PRIMARY KEY,
  name         VARCHAR(150) NOT NULL,
  rfc          VARCHAR(13)  NOT NULL UNIQUE,
  address      VARCHAR(255),
  phone        VARCHAR(30),
  email        VARCHAR(150),
  contact_name VARCHAR(150),
  client_type  VARCHAR(20)  NOT NULL DEFAULT 'occasional'
               CHECK (client_type IN ('occasional','frequent')),
  payment_terms VARCHAR(20) NOT NULL DEFAULT 'cash'
               CHECK (payment_terms IN ('cash','credit')),
  credit_limit DECIMAL(12,2) NOT NULL DEFAULT 0,
  credit_days  INT           NOT NULL DEFAULT 0,
  status       VARCHAR(20)  NOT NULL DEFAULT 'active'
               CHECK (status IN ('active','inactive')),
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_clients_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE routes (
  id           BIGINT PRIMARY KEY,
  origin       VARCHAR(150) NOT NULL,
  destination  VARCHAR(150) NOT NULL,
  estimated_km DECIMAL(10,1),
  description  VARCHAR(255),
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE client_rates (
  id         BIGINT PRIMARY KEY,
  client_id  BIGINT NOT NULL,
  route_id   BIGINT NOT NULL,
  rate       DECIMAL(12,2) NOT NULL,
  valid_from DATE NOT NULL,
  valid_to   DATE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_rates_client FOREIGN KEY (client_id) REFERENCES clients (id),
  CONSTRAINT fk_rates_route  FOREIGN KEY (route_id)  REFERENCES routes (id),
  INDEX idx_rates_lookup (client_id, route_id, valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- requests

CREATE TABLE service_requests (
  id                     BIGINT PRIMARY KEY,
  folio                  VARCHAR(20) NOT NULL UNIQUE,
  client_id              BIGINT NOT NULL,
  route_id               BIGINT NOT NULL,
  cargo_description      VARCHAR(255),
  estimated_weight       DECIMAL(10,1),
  pickup_date_scheduled  DATETIME,
  delivery_date_scheduled DATETIME,
  agreed_rate            DECIMAL(12,2),
  requires_documents     BOOLEAN NOT NULL DEFAULT TRUE,
  status                 VARCHAR(20) NOT NULL DEFAULT 'requested'
                         CHECK (status IN ('requested','authorized','scheduled','assigned','in_transit','delivered','closed','cancelled')),
  notes                  VARCHAR(500),
  created_by             BIGINT,
  updated_by             BIGINT,
  created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_sr_client     FOREIGN KEY (client_id)  REFERENCES clients (id),
  CONSTRAINT fk_sr_route      FOREIGN KEY (route_id)   REFERENCES routes (id),
  CONSTRAINT fk_sr_created_by FOREIGN KEY (created_by) REFERENCES users (id),
  CONSTRAINT fk_sr_updated_by FOREIGN KEY (updated_by) REFERENCES users (id),
  INDEX idx_sr_status (status),
  INDEX idx_sr_client (client_id),
  INDEX idx_sr_route (route_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- trips

CREATE TABLE trips (
  id                 BIGINT PRIMARY KEY,
  service_request_id BIGINT NOT NULL UNIQUE,
  vehicle_id         BIGINT NOT NULL,
  employee_id        BIGINT NOT NULL,
  estimated_km       DECIMAL(10,1),
  actual_km          DECIMAL(10,1),
  planned_start      DATETIME NOT NULL,
  planned_end        DATETIME NOT NULL,
  departure_datetime DATETIME,
  arrival_datetime   DATETIME,
  status             VARCHAR(20) NOT NULL DEFAULT 'scheduled'
                     CHECK (status IN ('scheduled','in_transit','completed','cancelled')),
  created_by         BIGINT,
  updated_by         BIGINT,
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_trip_request  FOREIGN KEY (service_request_id) REFERENCES service_requests (id),
  CONSTRAINT fk_trip_vehicle  FOREIGN KEY (vehicle_id)         REFERENCES vehicles (id),
  CONSTRAINT fk_trip_employee FOREIGN KEY (employee_id)        REFERENCES employees (id),
  CONSTRAINT fk_trip_created  FOREIGN KEY (created_by)         REFERENCES users (id),
  CONSTRAINT fk_trip_updated  FOREIGN KEY (updated_by)         REFERENCES users (id),
  INDEX idx_trips_vehicle_window (vehicle_id, planned_start),
  INDEX idx_trips_employee_window (employee_id, planned_start),
  INDEX idx_trips_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE expenses (
  id           BIGINT PRIMARY KEY,
  trip_id      BIGINT NOT NULL,
  expense_type VARCHAR(20) NOT NULL
               CHECK (expense_type IN ('tolls','food','parking','lodging','repairs','handling','permits','other')),
  amount       DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  expense_date DATE NOT NULL,
  description  VARCHAR(255),
  created_by   BIGINT,
  created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_expense_trip    FOREIGN KEY (trip_id)    REFERENCES trips (id),
  CONSTRAINT fk_expense_creator FOREIGN KEY (created_by) REFERENCES users (id),
  INDEX idx_expenses_trip (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE advances (
  id             BIGINT PRIMARY KEY,
  trip_id        BIGINT NOT NULL,
  employee_id    BIGINT NOT NULL,
  amount_given   DECIMAL(12,2) NOT NULL CHECK (amount_given > 0),
  delivered_date DATE NOT NULL,
  status         VARCHAR(20) NOT NULL DEFAULT 'pending'
                 CHECK (status IN ('pending','settled')),
  settled_at     DATETIME,
  settled_by     BIGINT,
  created_by     BIGINT,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_advance_trip     FOREIGN KEY (trip_id)     REFERENCES trips (id),
  CONSTRAINT fk_advance_employee FOREIGN KEY (employee_id) REFERENCES employees (id),
  CONSTRAINT fk_advance_settler  FOREIGN KEY (settled_by)  REFERENCES users (id),
  CONSTRAINT fk_advance_creator  FOREIGN KEY (created_by)  REFERENCES users (id),
  INDEX idx_advances_trip (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fuel_loads (
  id                BIGINT PRIMARY KEY,
  vehicle_id        BIGINT NOT NULL,
  trip_id           BIGINT,
  fuel_station      VARCHAR(100),
  load_date         DATETIME NOT NULL,
  liters            DECIMAL(8,2) NOT NULL CHECK (liters > 0),
  price_per_liter   DECIMAL(8,3) NOT NULL CHECK (price_per_liter > 0),
  amount            DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  odometer_reading  DECIMAL(10,1),
  created_by        BIGINT,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_fuel_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
  CONSTRAINT fk_fuel_trip    FOREIGN KEY (trip_id)    REFERENCES trips (id),
  CONSTRAINT fk_fuel_creator FOREIGN KEY (created_by) REFERENCES users (id),
  INDEX idx_fuel_vehicle_date (vehicle_id, load_date),
  INDEX idx_fuel_trip (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE maintenance (
  id                BIGINT PRIMARY KEY,
  vehicle_id        BIGINT NOT NULL,
  maintenance_date  DATE NOT NULL,
  odometer_reading  DECIMAL(10,1),
  maintenance_type  VARCHAR(20) NOT NULL DEFAULT 'preventive'
                    CHECK (maintenance_type IN ('preventive','corrective')),
  work_performed    VARCHAR(500),
  provider          VARCHAR(150),
  cost              DECIMAL(12,2) NOT NULL DEFAULT 0,
  next_service_date DATE,
  next_service_km   DECIMAL(10,1),
  created_by        BIGINT,
  created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_maint_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicles (id),
  CONSTRAINT fk_maint_creator FOREIGN KEY (created_by) REFERENCES users (id),
  INDEX idx_maint_vehicle (vehicle_id, maintenance_date),
  INDEX idx_maint_next (next_service_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE incidents (
  id             BIGINT PRIMARY KEY,
  trip_id        BIGINT NOT NULL,
  incident_date  DATE NOT NULL,
  incident_time  TIME,
  location       VARCHAR(150),
  incident_type  VARCHAR(30) NOT NULL
                 CHECK (incident_type IN ('accident','mechanical_failure','delay','road_closure','cargo_damage','documentation_issue','other')),
  description    VARCHAR(500),
  actions_taken  VARCHAR(500),
  created_by     BIGINT,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_incident_trip    FOREIGN KEY (trip_id)    REFERENCES trips (id),
  CONSTRAINT fk_incident_creator FOREIGN KEY (created_by) REFERENCES users (id),
  INDEX idx_incidents_trip (trip_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- one delivery per trip (BR-12)
CREATE TABLE deliveries (
  id                 BIGINT PRIMARY KEY,
  trip_id            BIGINT NOT NULL UNIQUE,
  actual_datetime    DATETIME,
  received_by        VARCHAR(150),
  evidence_reference VARCHAR(255),
  status             VARCHAR(20) NOT NULL DEFAULT 'pending_documents'
                     CHECK (status IN ('pending_documents','complete')),
  created_by         BIGINT,
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_delivery_trip    FOREIGN KEY (trip_id)    REFERENCES trips (id),
  CONSTRAINT fk_delivery_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- finance

-- one invoice per request in v1 (BR-20)
CREATE TABLE invoices (
  id                 BIGINT PRIMARY KEY,
  client_id          BIGINT NOT NULL,
  service_request_id BIGINT NOT NULL UNIQUE,
  invoice_number     VARCHAR(30) NOT NULL UNIQUE,
  amount             DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  issue_date         DATE NOT NULL,
  due_date           DATE NOT NULL,
  status             VARCHAR(20) NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending','paid','overdue','cancelled')),
  created_by         BIGINT,
  created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_invoice_client  FOREIGN KEY (client_id)          REFERENCES clients (id),
  CONSTRAINT fk_invoice_request FOREIGN KEY (service_request_id) REFERENCES service_requests (id),
  CONSTRAINT fk_invoice_creator FOREIGN KEY (created_by)         REFERENCES users (id),
  INDEX idx_invoices_status_due (status, due_date),
  INDEX idx_invoices_client (client_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
  id             BIGINT PRIMARY KEY,
  invoice_id     BIGINT NOT NULL,
  amount         DECIMAL(12,2) NOT NULL CHECK (amount > 0),
  payment_date   DATE NOT NULL,
  payment_method VARCHAR(30) NOT NULL DEFAULT 'cash'
                 CHECK (payment_method IN ('cash','transfer','check','card','other')),
  created_by     BIGINT,
  created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_payment_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
  CONSTRAINT fk_payment_creator FOREIGN KEY (created_by) REFERENCES users (id),
  INDEX idx_payments_invoice (invoice_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- audit

CREATE TABLE audit_log (
  id         BIGINT PRIMARY KEY,
  user_id    BIGINT,
  entity     VARCHAR(50) NOT NULL,
  entity_id  BIGINT,
  action     VARCHAR(50) NOT NULL,
  details    VARCHAR(500),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id),
  INDEX idx_audit_entity (entity, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- sequences
-- No AUTO_INCREMENT anywhere: the application allocates ids here, inside a
-- transaction, so concurrent writers cannot collide.

CREATE TABLE sequences (
  name       VARCHAR(50) NOT NULL PRIMARY KEY,
  next_value BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------- minimal seed
-- Enough to log in. Full demo data lives in db/init/02-seed.sql.
-- Password for admin is "admin123" (bcrypt), dev only.

INSERT INTO roles (id, name) VALUES
  (1, 'admin'), (2, 'traffic'), (3, 'maintenance'), (4, 'collections'), (5, 'viewer');

INSERT INTO permissions (id, name) VALUES
  (1, 'clients.read'), (2, 'clients.write'), (3, 'routes.read'), (4, 'routes.write'),
  (5, 'rates.read'), (6, 'rates.write'), (7, 'operators.read'), (8, 'operators.write'),
  (9, 'fleet.read'), (10, 'fleet.write'), (11, 'fleet.maintenance'),
  (12, 'fuel.read'), (13, 'fuel.write'), (14, 'requests.read'), (15, 'requests.write'),
  (16, 'requests.assign'), (17, 'trips.read'), (18, 'trips.write'), (19, 'trips.assign'),
  (20, 'expenses.read'), (21, 'expenses.write'), (22, 'advances.read'), (23, 'advances.write'),
  (24, 'deliveries.read'), (25, 'deliveries.write'), (26, 'incidents.read'), (27, 'incidents.write'),
  (28, 'invoices.read'), (29, 'invoices.write'), (30, 'payments.read'), (31, 'payments.write'),
  (32, 'reports.view'), (33, 'security.users');

INSERT INTO role_permissions (role_id, permission_id)
SELECT 1, id FROM permissions;

INSERT INTO users (id, username, password_hash, role_id, status) VALUES
  (1, 'admin',
   '$2a$10$eyAVHtcAySDTLjNDlIGuROCBMcOg3GfQrm9pWOYuAISimjSIJjd.y', 1, 'active');

INSERT INTO sequences (name, next_value)
SELECT 'users', COALESCE(MAX(id), 0) FROM users;
