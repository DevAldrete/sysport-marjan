-- SysPort - MARJAN :: schema
-- MySQL 8.4. English status codes, DECIMAL money, ids allocated from the
-- `sequences` table (no AUTO_INCREMENT). Stored procedures/functions hold the
-- application's business rules and are called by the repositories.
-- Design notes: the child column always references the parent id (PRD 4.1 F1).
-- Foreign keys are declared inline so table creation order is self-documenting.

CREATE DATABASE IF NOT EXISTS sysportdb;
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

CREATE TABLE sequences (
  name       VARCHAR(50) NOT NULL PRIMARY KEY,
  next_value BIGINT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ================================================================ routines
-- Reference implementation of the application's business rules and read
-- queries as SQL stored programs. The application does NOT call these; the
-- same logic lives in Java (rules/ + service/) so it can be unit-tested with
-- records and no database. This section only demonstrates that the rules
-- could live in the database instead.
--
-- Conventions
--   * Rule functions return 1/0 as a boolean (or a computed value).
--   * Validation/action procedures report problems through OUT p_problems:
--     NULL means "no problems". Action procedures run in a transaction and
--     roll back when any rule fails.
--   * Ids are allocated through the sequences table (sp_next_id), matching
--     the no-AUTO_INCREMENT policy of the application.
--
-- Business-rule map: BR-01 folio, BR-02 RFC, BR-03 state machine, BR-04 rate
-- snapshot, BR-05/06 overlaps, BR-07/11 assignable vehicle, BR-08 capacity,
-- BR-09/10 licence, BR-13 closing, BR-16 advance, BR-17 expense, BR-18 fuel,
-- BR-19/20 invoicing. Functional refs: FR-TRP-1/2, FR-DEL-2, FR-RPT-*.

DELIMITER $$

-- ---------------------------------------------------------------- helpers

-- BR-01: next human folio for a year, e.g. SR-2026-000123.
CREATE FUNCTION fn_request_can_transition(p_from VARCHAR(20), p_to VARCHAR(20))
RETURNS TINYINT
DETERMINISTIC
BEGIN
  RETURN CASE
    WHEN p_to = 'cancelled' AND p_from IN ('requested','authorized','scheduled','assigned') THEN 1
    WHEN p_from = 'requested'  AND p_to = 'authorized' THEN 1
    WHEN p_from = 'authorized' AND p_to = 'scheduled'  THEN 1
    WHEN p_from = 'scheduled'  AND p_to = 'assigned'   THEN 1
    WHEN p_from = 'assigned'   AND p_to = 'in_transit' THEN 1
    WHEN p_from = 'in_transit' AND p_to = 'delivered'  THEN 1
    WHEN p_from = 'delivered'  AND p_to = 'closed'     THEN 1
    ELSE 0
  END;
END$$

-- BR-07 / BR-11: only 'available' vehicles may be assigned.
CREATE FUNCTION fn_vehicle_assignable(p_status VARCHAR(20))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN p_status = 'available';
END$$

-- BR-09: only 'available' operators may be assigned.
CREATE FUNCTION fn_employee_assignable(p_status VARCHAR(20))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN p_status = 'available';
END$$

-- BR-05/06: a trip occupies a resource only while scheduled or in transit.
CREATE FUNCTION fn_trip_is_active(p_status VARCHAR(20))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN p_status IN ('scheduled','in_transit');
END$$

-- BR-08: capacity is fine when either side is unknown, else capacity >= weight.
CREATE FUNCTION fn_capacity_ok(p_capacity DECIMAL(10,1), p_weight DECIMAL(10,1))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_capacity IS NULL OR p_weight IS NULL OR p_capacity >= p_weight);
END$$

-- BR-18: amount must be within tolerance of liters x price.
CREATE FUNCTION fn_amount_matches(p_liters DECIMAL(8,2), p_price DECIMAL(8,3),
    p_amount DECIMAL(12,2), p_tolerance DECIMAL(6,3))
RETURNS TINYINT DETERMINISTIC
BEGIN
  IF p_liters IS NULL OR p_price IS NULL OR p_amount IS NULL THEN
    RETURN 0;
  END IF;
  RETURN ABS(p_liters * p_price - p_amount) <= COALESCE(p_tolerance, 0.05);
END$$

-- BR-16: positive -> operator owes; negative -> company owes; zero -> settled.
CREATE FUNCTION fn_advance_balance(p_given DECIMAL(12,2), p_expenses DECIMAL(12,2),
    p_fuel DECIMAL(12,2))
RETURNS DECIMAL(14,2) DETERMINISTIC
BEGIN
  RETURN COALESCE(p_given,0) - COALESCE(p_expenses,0) - COALESCE(p_fuel,0);
END$$

-- BR-09/10: expired when the date is before today.
CREATE FUNCTION fn_license_expired(p_expiration DATE, p_today DATE)
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_expiration IS NOT NULL AND p_expiration < p_today);
END$$

-- BR-10: expiring soon (not yet expired, within p_days).
CREATE FUNCTION fn_license_expiring(p_expiration DATE, p_days INT, p_today DATE)
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_expiration IS NOT NULL AND p_expiration >= p_today
          AND DATEDIFF(p_expiration, p_today) <= p_days);
END$$

-- BR-19: cash due on issue; credit due issue + credit_days.
CREATE FUNCTION fn_invoice_due_date(p_issue DATE, p_terms VARCHAR(20), p_credit_days INT)
RETURNS DATE DETERMINISTIC
BEGIN
  IF p_terms = 'credit' THEN
    RETURN DATE_ADD(p_issue, INTERVAL GREATEST(COALESCE(p_credit_days,0),0) DAY);
  END IF;
  RETURN p_issue;
END$$

-- BR-19: paid when payments cover the amount; overdue when past due and unpaid.
CREATE FUNCTION fn_invoice_status(p_current VARCHAR(20), p_amount DECIMAL(12,2),
    p_paid DECIMAL(12,2), p_due DATE, p_today DATE)
RETURNS VARCHAR(20) DETERMINISTIC
BEGIN
  IF p_current = 'cancelled' THEN
    RETURN 'cancelled';
  END IF;
  IF COALESCE(p_paid,0) >= p_amount THEN
    RETURN 'paid';
  END IF;
  IF p_due IS NOT NULL AND p_due < p_today THEN
    RETURN 'overdue';
  END IF;
  RETURN 'pending';
END$$

-- BR-02: RFC format (3-4 letters, 6 digits, 3 alphanumerics).
CREATE FUNCTION fn_rfc_valid(p_rfc VARCHAR(13))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_rfc IS NOT NULL AND p_rfc REGEXP '^[A-ZÑ&]{3,4}[0-9]{6}[A-Z0-9]{3}$');
END$$

CREATE FUNCTION fn_email_valid(p_email VARCHAR(150))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_email IS NOT NULL AND p_email REGEXP '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+[.][A-Za-z]{2,}$');
END$$

CREATE FUNCTION fn_phone_valid(p_phone VARCHAR(30))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_phone IS NOT NULL AND p_phone REGEXP '^[0-9+(). -]{7,20}$');
END$$

-- BR-01: next folio sequence for a year (mirrors FolioGenerator + nextSequence).
CREATE FUNCTION fn_next_folio(p_year INT)
RETURNS VARCHAR(20)
READS SQL DATA
BEGIN
  RETURN (SELECT CONCAT('SR-', p_year, '-',
      LPAD(COALESCE(MAX(CAST(SUBSTRING(folio, 9) AS UNSIGNED)), 0) + 1, 6, '0'))
      FROM service_requests WHERE folio LIKE CONCAT('SR-', p_year, '-%'));
END$$

-- BR-20: next invoice number for a year.
CREATE FUNCTION fn_next_invoice_number(p_year INT)
RETURNS VARCHAR(30)
READS SQL DATA
BEGIN
  RETURN (SELECT CONCAT('INV-', p_year, '-',
      LPAD(COALESCE(MAX(CAST(SUBSTRING(invoice_number, 10) AS UNSIGNED)), 0) + 1, 6, '0'))
      FROM invoices WHERE invoice_number LIKE CONCAT('INV-', p_year, '-%'));
END$$

-- Allocates the next id for a table from the sequences row (same policy as
-- SequenceRepository: no AUTO_INCREMENT, primed from max(id) when missing).
CREATE PROCEDURE sp_next_id(IN p_name VARCHAR(50), OUT p_id BIGINT)
p: BEGIN
  DECLARE v_updated INT DEFAULT 0;
  UPDATE sequences SET next_value = next_value + 1 WHERE name = p_name;
  SET v_updated = ROW_COUNT();
  IF v_updated = 0 THEN
    SET @seq_table := p_name;
    SET @seq_max := 0;
    SET @seq_sql := CONCAT('SELECT COALESCE(MAX(id),0) INTO @seq_max FROM `', @seq_table, '`');
    PREPARE seq_stmt FROM @seq_sql;
    EXECUTE seq_stmt;
    DEALLOCATE PREPARE seq_stmt;
    INSERT INTO sequences(name, next_value) VALUES (p_name, @seq_max);
    UPDATE sequences SET next_value = next_value + 1 WHERE name = p_name;
  END IF;
  SELECT next_value INTO p_id FROM sequences WHERE name = p_name;
END$$

-- ------------------------------------------------- rules: validate a trip assignment

-- FR-TRP-1 / BR-05..BR-11 for the vehicle half. Collects all problems.
CREATE PROCEDURE sp_validate_vehicle_assignment(IN p_request_id BIGINT,
    IN p_vehicle_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_req_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_start DATETIME DEFAULT NULL;
  DECLARE v_end DATETIME DEFAULT NULL;
  DECLARE v_weight DECIMAL(10,1) DEFAULT NULL;
  DECLARE v_veh_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_capacity DECIMAL(10,1) DEFAULT NULL;
  DECLARE v_conflicts INT DEFAULT 0;

  SET p_problems = NULL;
  SELECT status, pickup_date_scheduled, delivery_date_scheduled, estimated_weight
    INTO v_req_status, v_start, v_end, v_weight
    FROM service_requests WHERE id = p_request_id;
  IF v_req_status IS NULL THEN
    SET p_problems = 'Solicitud no encontrada';
    LEAVE p;
  END IF;
  IF v_req_status <> 'scheduled' THEN
    SET p_problems = CONCAT_WS('; ', p_problems,
        'La solicitud debe estar programada para poder asignarle un viaje');
  END IF;
  IF v_start IS NULL OR v_end IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La solicitud no tiene fechas programadas');
  END IF;

  SELECT status, load_capacity INTO v_veh_status, v_capacity
    FROM vehicles WHERE id = p_vehicle_id;
  IF v_veh_status IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar una unidad');
  ELSE
    IF NOT fn_vehicle_assignable(v_veh_status) THEN
      SET p_problems = CONCAT_WS('; ', p_problems,
          CONCAT('La unidad no esta disponible (', v_veh_status, ')'));
    END IF;
    IF NOT fn_capacity_ok(v_capacity, v_weight) THEN
      SET p_problems = CONCAT_WS('; ', p_problems,
          CONCAT('La capacidad de la unidad (', v_capacity,
                 ' kg) es menor al peso estimado (', v_weight, ' kg)'));
    END IF;
    IF v_start IS NOT NULL AND v_end IS NOT NULL THEN
      SELECT COUNT(*) INTO v_conflicts FROM trips
        WHERE vehicle_id = p_vehicle_id
          AND status IN ('scheduled','in_transit')
          AND planned_start < v_end AND planned_end > v_start;
      IF v_conflicts > 0 THEN
        SET p_problems = CONCAT_WS('; ', p_problems,
            'La unidad ya tiene un viaje en ese periodo');
      END IF;
    END IF;
  END IF;
END$$

-- FR-TRP-1 / BR-06, BR-09, BR-10 for the operator half.
CREATE PROCEDURE sp_validate_operator_assignment(IN p_request_id BIGINT,
    IN p_operator_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_start DATETIME DEFAULT NULL;
  DECLARE v_end DATETIME DEFAULT NULL;
  DECLARE v_emp_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_exp DATE DEFAULT NULL;
  DECLARE v_conflicts INT DEFAULT 0;

  SET p_problems = NULL;
  SELECT pickup_date_scheduled, delivery_date_scheduled INTO v_start, v_end
    FROM service_requests WHERE id = p_request_id;
  IF v_end IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems,
        'La solicitud no tiene fecha de entrega programada');
  END IF;

  SELECT e.status, l.expiration_date INTO v_emp_status, v_exp
    FROM employees e LEFT JOIN licenses l ON l.id = e.license_id
    WHERE e.id = p_operator_id;
  IF v_emp_status IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un operador');
  ELSE
    IF NOT fn_employee_assignable(v_emp_status) THEN
      SET p_problems = CONCAT_WS('; ', p_problems,
          CONCAT('El operador no esta disponible (', v_emp_status, ')'));
    END IF;
    IF v_exp IS NULL THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'El operador no tiene licencia registrada');
    ELSEIF v_end IS NOT NULL AND v_exp < DATE(v_end) THEN
      SET p_problems = CONCAT_WS('; ', p_problems,
          CONCAT('La licencia del operador vence el ', v_exp, ', antes del fin del viaje'));
    END IF;
    IF v_start IS NOT NULL AND v_end IS NOT NULL THEN
      SELECT COUNT(*) INTO v_conflicts FROM trips
        WHERE employee_id = p_operator_id
          AND status IN ('scheduled','in_transit')
          AND planned_start < v_end AND planned_end > v_start;
      IF v_conflicts > 0 THEN
        SET p_problems = CONCAT_WS('; ', p_problems,
            'El operador ya tiene un viaje en ese periodo');
      END IF;
    END IF;
  END IF;
END$$

-- BR-18: fuel amount consistency, odometer monotonicity and trip/vehicle match.
CREATE PROCEDURE sp_validate_fuel_load(IN p_vehicle_id BIGINT, IN p_trip_id BIGINT,
    IN p_liters DECIMAL(8,2), IN p_price DECIMAL(8,3), IN p_amount DECIMAL(12,2),
    IN p_odometer DECIMAL(10,1), OUT p_problems TEXT)
p: BEGIN
  DECLARE v_mileage DECIMAL(10,1) DEFAULT NULL;
  DECLARE v_trip_vehicle BIGINT DEFAULT NULL;

  SET p_problems = NULL;
  IF p_liters IS NULL OR p_liters <= 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Los litros son invalidos o exceden el maximo permitido');
  END IF;
  IF p_price IS NULL OR p_price <= 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El precio por litro es invalido o excede el maximo permitido');
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El importe debe ser mayor a cero y dentro del rango permitido');
  END IF;
  IF NOT fn_amount_matches(p_liters, p_price, p_amount, 0.05) THEN
    SET p_problems = CONCAT_WS('; ', p_problems,
        CONCAT('El importe no coincide con litros x precio (esperado ',
               ROUND(p_liters * p_price, 2), ')'));
  END IF;
  SELECT mileage INTO v_mileage FROM vehicles WHERE id = p_vehicle_id;
  IF p_odometer IS NOT NULL AND v_mileage IS NOT NULL AND p_odometer < v_mileage THEN
    SET p_problems = CONCAT_WS('; ', p_problems,
        'El odometro no puede ser menor al kilometraje actual de la unidad');
  END IF;
  IF p_trip_id IS NOT NULL THEN
    SELECT vehicle_id INTO v_trip_vehicle FROM trips WHERE id = p_trip_id;
    IF v_trip_vehicle IS NOT NULL AND v_trip_vehicle <> p_vehicle_id THEN
      SET p_problems = CONCAT_WS('; ', p_problems,
          'La unidad de la carga no coincide con la unidad del viaje');
    END IF;
  END IF;
END$$

-- BR-17: expense type required, amount positive, date required.
CREATE PROCEDURE sp_validate_expense(IN p_trip_id BIGINT, IN p_type VARCHAR(20),
    IN p_amount DECIMAL(12,2), IN p_date DATE, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_trip_id IS NULL OR p_trip_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un viaje');
  END IF;
  IF p_type IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un tipo de gasto');
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El importe debe ser mayor a cero y dentro del rango permitido');
  END IF;
  IF p_date IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha del gasto es obligatoria');
  END IF;
END$$

-- BR-16: advance amount positive, trip/employee/date required.
CREATE PROCEDURE sp_validate_advance(IN p_trip_id BIGINT, IN p_employee_id BIGINT,
    IN p_amount DECIMAL(12,2), IN p_date DATE, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_trip_id IS NULL OR p_trip_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un viaje');
  END IF;
  IF p_employee_id IS NULL OR p_employee_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un operador');
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El monto del anticipo debe ser mayor a cero y dentro del rango permitido');
  END IF;
  IF p_date IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de entrega del anticipo es obligatoria');
  END IF;
END$$

-- ------------------------------------------------- actions (transactional)

-- BR-03 + BR-04: authorize only from requested, with a positive rate.
CREATE PROCEDURE sp_authorize_request(IN p_request_id BIGINT, IN p_rate DECIMAL(12,2),
    IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_audit_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK;
    SET p_problems = 'Error inesperado al autorizar la solicitud';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status INTO v_status FROM service_requests WHERE id = p_request_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;
  IF NOT fn_request_can_transition(v_status, 'authorized') THEN
    ROLLBACK; SET p_problems = CONCAT('No se puede pasar de ', v_status, ' a authorized'); LEAVE p;
  END IF;
  IF p_rate IS NULL OR p_rate <= 0 THEN
    ROLLBACK; SET p_problems = 'La tarifa acordada debe ser mayor a cero'; LEAVE p;
  END IF;
  UPDATE service_requests SET agreed_rate = p_rate, status = 'authorized', updated_by = p_user_id
    WHERE id = p_request_id;
  CALL sp_next_id('audit_log', v_audit_id);
  INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
    VALUES (v_audit_id, p_user_id, 'service_request', p_request_id, 'authorized', CONCAT('rate=', p_rate));
  COMMIT;
END$$

-- BR-03: schedule only from authorized; delivery must be after pickup.
CREATE PROCEDURE sp_schedule_request(IN p_request_id BIGINT, IN p_pickup DATETIME,
    IN p_delivery DATETIME, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al programar la solicitud';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status INTO v_status FROM service_requests WHERE id = p_request_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;
  IF NOT fn_request_can_transition(v_status, 'scheduled') THEN
    ROLLBACK; SET p_problems = CONCAT('No se puede pasar de ', v_status, ' a scheduled'); LEAVE p;
  END IF;
  IF p_pickup IS NULL OR p_delivery IS NULL THEN
    ROLLBACK; SET p_problems = 'Las fechas programadas de recoleccion y entrega son obligatorias'; LEAVE p;
  END IF;
  IF p_delivery <= p_pickup THEN
    ROLLBACK; SET p_problems = 'La fecha de entrega debe ser posterior a la de recoleccion'; LEAVE p;
  END IF;
  UPDATE service_requests
    SET pickup_date_scheduled = p_pickup, delivery_date_scheduled = p_delivery,
        status = 'scheduled', updated_by = p_user_id
    WHERE id = p_request_id;
  COMMIT;
END$$

-- BR-03: cancel a request that has not started; a reason is required.
CREATE PROCEDURE sp_cancel_request(IN p_request_id BIGINT, IN p_reason VARCHAR(500),
    IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al cancelar la solicitud';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status INTO v_status FROM service_requests WHERE id = p_request_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;
  IF NOT fn_request_can_transition(v_status, 'cancelled') THEN
    ROLLBACK; SET p_problems = CONCAT('No se puede pasar de ', v_status, ' a cancelled'); LEAVE p;
  END IF;
  IF p_reason IS NULL OR p_reason = '' THEN
    ROLLBACK; SET p_problems = 'Debe indicar el motivo de la cancelacion'; LEAVE p;
  END IF;
  UPDATE service_requests SET notes = p_reason, status = 'cancelled', updated_by = p_user_id
    WHERE id = p_request_id;
  COMMIT;
END$$

-- FR-TRP-2 / BR-05..BR-11: validate everything, insert the trip, mark the
-- request assigned, all under row locks so two dispatchers cannot double-book.
CREATE PROCEDURE sp_assign_trip(IN p_request_id BIGINT, IN p_vehicle_id BIGINT,
    IN p_operator_id BIGINT, IN p_user_id BIGINT,
    OUT p_problems TEXT, OUT p_trip_id BIGINT)
p: BEGIN
  DECLARE v_req_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_start DATETIME DEFAULT NULL;
  DECLARE v_end DATETIME DEFAULT NULL;
  DECLARE v_audit_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al asignar el viaje';
  END;

  SET p_problems = NULL;
  SET p_trip_id = NULL;
  START TRANSACTION;

  SELECT id INTO @lock_v FROM vehicles WHERE id = p_vehicle_id FOR UPDATE;
  SELECT id INTO @lock_e FROM employees WHERE id = p_operator_id FOR UPDATE;

  SELECT status, pickup_date_scheduled, delivery_date_scheduled
    INTO v_req_status, v_start, v_end
    FROM service_requests WHERE id = p_request_id;
  IF v_req_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;

  CALL validate_vehicle_assignment_into(p_request_id, p_vehicle_id, p_problems);
  CALL validate_operator_assignment_into(p_request_id, p_operator_id, p_problems);
  IF p_problems IS NOT NULL THEN
    ROLLBACK; LEAVE p;
  END IF;

  CALL sp_next_id('trips', p_trip_id);
  INSERT INTO trips (id, service_request_id, vehicle_id, employee_id,
                     planned_start, planned_end, status, created_by, updated_by)
    VALUES (p_trip_id, p_request_id, p_vehicle_id, p_operator_id,
            v_start, v_end, 'scheduled', p_user_id, p_user_id);
  UPDATE service_requests SET status = 'assigned', updated_by = p_user_id WHERE id = p_request_id;
  CALL sp_next_id('audit_log', v_audit_id);
  INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
    VALUES (v_audit_id, p_user_id, 'trip', p_trip_id, 'assigned',
            CONCAT('vehicle=', p_vehicle_id, ', operator=', p_operator_id));
  COMMIT;
END$$

-- Records departure: trip/request -> in_transit, resources -> on_trip.
CREATE PROCEDURE sp_depart_trip(IN p_trip_id BIGINT, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_vehicle BIGINT;
  DECLARE v_employee BIGINT;
  DECLARE v_request BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al registrar la salida';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status, vehicle_id, employee_id, service_request_id
    INTO v_status, v_vehicle, v_employee, v_request
    FROM trips WHERE id = p_trip_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Viaje no encontrado'; LEAVE p;
  END IF;
  IF v_status <> 'scheduled' THEN
    ROLLBACK; SET p_problems = 'El viaje no esta programado'; LEAVE p;
  END IF;
  UPDATE trips SET departure_datetime = NOW(), status = 'in_transit', updated_by = p_user_id
    WHERE id = p_trip_id;
  UPDATE vehicles SET status = 'on_trip' WHERE id = v_vehicle;
  UPDATE employees SET status = 'on_trip' WHERE id = v_employee;
  UPDATE service_requests SET status = 'in_transit', updated_by = p_user_id WHERE id = v_request;
  COMMIT;
END$$

-- Records arrival: trip completed, resources freed, mileage advanced (BR-21).
CREATE PROCEDURE sp_arrive_trip(IN p_trip_id BIGINT, IN p_actual_km DECIMAL(10,1),
    IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_vehicle BIGINT;
  DECLARE v_employee BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al registrar la llegada';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status, vehicle_id, employee_id INTO v_status, v_vehicle, v_employee
    FROM trips WHERE id = p_trip_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Viaje no encontrado'; LEAVE p;
  END IF;
  IF v_status <> 'in_transit' THEN
    ROLLBACK; SET p_problems = 'El viaje no esta en transito'; LEAVE p;
  END IF;
  UPDATE trips SET arrival_datetime = NOW(), actual_km = p_actual_km, status = 'completed',
                   updated_by = p_user_id
    WHERE id = p_trip_id;
  UPDATE vehicles SET status = 'available' WHERE id = v_vehicle;
  UPDATE vehicles SET mileage = p_actual_km WHERE id = v_vehicle AND p_actual_km IS NOT NULL
    AND mileage < p_actual_km;
  UPDATE employees SET status = 'available' WHERE id = v_employee;
  COMMIT;
END$$

-- Cancels a scheduled trip and its request, freeing the resources.
CREATE PROCEDURE sp_cancel_trip(IN p_trip_id BIGINT, IN p_reason VARCHAR(500),
    IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_request BIGINT;
  DECLARE v_audit_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al cancelar el viaje';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status, service_request_id INTO v_status, v_request
    FROM trips WHERE id = p_trip_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Viaje no encontrado'; LEAVE p;
  END IF;
  IF v_status <> 'scheduled' THEN
    ROLLBACK; SET p_problems = 'Solo se puede cancelar un viaje programado'; LEAVE p;
  END IF;
  IF p_reason IS NULL OR p_reason = '' THEN
    ROLLBACK; SET p_problems = 'Debe indicar el motivo de la cancelacion'; LEAVE p;
  END IF;
  UPDATE trips SET status = 'cancelled', updated_by = p_user_id WHERE id = p_trip_id;
  UPDATE service_requests SET notes = p_reason, status = 'cancelled', updated_by = p_user_id
    WHERE id = v_request;
  CALL sp_next_id('audit_log', v_audit_id);
  INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
    VALUES (v_audit_id, p_user_id, 'trip', p_trip_id, 'cancelled', p_reason);
  COMMIT;
END$$

-- FR-DEL-2 / BR-13: close a delivered request only when its delivery is complete.
CREATE PROCEDURE sp_close_request(IN p_request_id BIGINT, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_requires TINYINT DEFAULT 1;
  DECLARE v_delivery_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_received VARCHAR(150);
  DECLARE v_evidence VARCHAR(255);
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al cerrar la solicitud';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status, requires_documents INTO v_status, v_requires
    FROM service_requests WHERE id = p_request_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;
  IF NOT fn_request_can_transition(v_status, 'closed') THEN
    ROLLBACK; SET p_problems = 'Solo se puede cerrar una solicitud entregada'; LEAVE p;
  END IF;

  IF v_requires THEN
    SELECT d.status, d.received_by, d.evidence_reference
      INTO v_delivery_status, v_received, v_evidence
      FROM deliveries d JOIN trips t ON t.id = d.trip_id
      WHERE t.service_request_id = p_request_id;
    IF v_delivery_status IS NULL THEN
      ROLLBACK; SET p_problems = 'La solicitud requiere documentacion y no tiene entrega registrada'; LEAVE p;
    END IF;
    IF v_delivery_status <> 'complete' OR v_received IS NULL OR v_received = ''
       OR v_evidence IS NULL OR v_evidence = '' THEN
      ROLLBACK; SET p_problems = 'La entrega no esta completa (recibido por y evidencia son obligatorios)'; LEAVE p;
    END IF;
  END IF;

  UPDATE service_requests SET status = 'closed', updated_by = p_user_id WHERE id = p_request_id;
  COMMIT;
END$$

-- BR-19: register a payment and re-derive the invoice status, in one transaction.
CREATE PROCEDURE sp_register_payment(IN p_invoice_id BIGINT, IN p_amount DECIMAL(12,2),
    IN p_date DATE, IN p_method VARCHAR(30), IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_amount DECIMAL(12,2) DEFAULT NULL;
  DECLARE v_paid DECIMAL(12,2) DEFAULT 0;
  DECLARE v_due DATE;
  DECLARE v_status VARCHAR(20);
  DECLARE v_payment_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al registrar el pago';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT amount, due_date, status INTO v_amount, v_due, v_status
    FROM invoices WHERE id = p_invoice_id FOR UPDATE;
  IF v_amount IS NULL THEN
    ROLLBACK; SET p_problems = 'Factura no encontrada'; LEAVE p;
  END IF;
  SELECT COALESCE(SUM(amount),0) INTO v_paid FROM payments WHERE invoice_id = p_invoice_id;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    ROLLBACK; SET p_problems = 'El pago debe ser mayor a cero y dentro del rango permitido'; LEAVE p;
  END IF;
  IF v_paid + p_amount > v_amount THEN
    ROLLBACK; SET p_problems = CONCAT('El pago excede el saldo pendiente (', v_amount - v_paid, ')'); LEAVE p;
  END IF;
  CALL sp_next_id('payments', v_payment_id);
  INSERT INTO payments (id, invoice_id, amount, payment_date, payment_method, created_by)
    VALUES (v_payment_id, p_invoice_id, p_amount, COALESCE(p_date, CURDATE()),
            COALESCE(p_method, 'cash'), p_user_id);
  UPDATE invoices SET status = fn_invoice_status(v_status, v_amount, v_paid + p_amount, v_due, CURDATE())
    WHERE id = p_invoice_id;
  COMMIT;
END$$

-- BR-19 / FR-INV-3: recompute paid/overdue for every open invoice.
CREATE PROCEDURE sp_refresh_invoice_statuses(IN p_today DATE, OUT p_changed INT)
p: BEGIN
  UPDATE invoices i
    SET i.status = fn_invoice_status(i.status, i.amount,
        COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0),
        i.due_date, p_today)
    WHERE i.status <> 'cancelled'
      AND i.status <> fn_invoice_status(i.status, i.amount,
          COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0),
          i.due_date, p_today);
  SET p_changed = ROW_COUNT();
END$$

-- BR-20: one invoice per delivered/closed request; due date from payment terms.
CREATE PROCEDURE sp_create_invoice_from_request(IN p_request_id BIGINT, IN p_issue DATE,
    IN p_amount DECIMAL(12,2), IN p_user_id BIGINT,
    OUT p_problems TEXT, OUT p_invoice_id BIGINT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_rate DECIMAL(12,2);
  DECLARE v_client BIGINT;
  DECLARE v_terms VARCHAR(20);
  DECLARE v_credit_days INT;
  DECLARE v_issue DATE;
  DECLARE v_due DATE;
  DECLARE v_amount DECIMAL(12,2);
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al crear la factura';
  END;

  SET p_problems = NULL;
  SET p_invoice_id = NULL;
  START TRANSACTION;
  SELECT sr.status, sr.agreed_rate, sr.client_id, c.payment_terms, c.credit_days
    INTO v_status, v_rate, v_client, v_terms, v_credit_days
    FROM service_requests sr JOIN clients c ON c.id = sr.client_id
    WHERE sr.id = p_request_id;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;
  IF v_status NOT IN ('delivered','closed') THEN
    ROLLBACK; SET p_problems = 'Solo se puede facturar una solicitud entregada o cerrada'; LEAVE p;
  END IF;
  IF v_rate IS NULL OR v_rate <= 0 THEN
    ROLLBACK; SET p_problems = 'La solicitud no tiene tarifa acordada'; LEAVE p;
  END IF;
  IF (SELECT COUNT(*) FROM invoices WHERE service_request_id = p_request_id) > 0 THEN
    ROLLBACK; SET p_problems = 'La solicitud ya tiene una factura'; LEAVE p;
  END IF;
  SET v_amount = COALESCE(p_amount, v_rate);
  IF v_amount <= 0 THEN
    ROLLBACK; SET p_problems = 'El importe de la factura debe ser mayor a cero'; LEAVE p;
  END IF;
  SET v_issue = COALESCE(p_issue, CURDATE());
  SET v_due = fn_invoice_due_date(v_issue, v_terms, v_credit_days);
  CALL sp_next_id('invoices', p_invoice_id);
  INSERT INTO invoices (id, client_id, service_request_id, invoice_number, amount,
                        issue_date, due_date, status, created_by)
    VALUES (p_invoice_id, v_client, p_request_id, fn_next_invoice_number(YEAR(v_issue)),
            v_amount, v_issue, v_due, 'pending', p_user_id);
  COMMIT;
END$$

-- BR-16: settle an advance (marks it settled, records who/when).
CREATE PROCEDURE sp_settle_advance(IN p_advance_id BIGINT, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  UPDATE advances SET status = 'settled', settled_at = NOW(), settled_by = p_user_id
    WHERE id = p_advance_id AND status = 'pending';
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Anticipo no encontrado o ya comprobado';
  END IF;
END$$

-- BR-16: advance balance for a trip = given - (expenses + fuel).
CREATE PROCEDURE sp_advance_balance(IN p_trip_id BIGINT,
    OUT p_given DECIMAL(14,2), OUT p_proven DECIMAL(14,2), OUT p_balance DECIMAL(14,2))
p: BEGIN
  SELECT COALESCE(SUM(amount_given),0) INTO p_given FROM advances WHERE trip_id = p_trip_id;
  SELECT COALESCE((SELECT SUM(amount) FROM expenses WHERE trip_id = p_trip_id),0)
       + COALESCE((SELECT SUM(amount) FROM fuel_loads WHERE trip_id = p_trip_id),0)
    INTO p_proven;
  SET p_balance = fn_advance_balance(p_given, p_proven, 0);
END$$

-- ------------------------------------------------- read queries (result sets)

-- FR-TRP-1: vehicles available with no overlapping active trip in the window.
CREATE PROCEDURE sp_eligible_vehicles(IN p_start DATETIME, IN p_end DATETIME)
p: BEGIN
  SELECT v.* FROM vehicles v
  WHERE v.status = 'available'
    AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.vehicle_id = v.id
        AND t.status IN ('scheduled','in_transit')
        AND t.planned_start < p_end AND t.planned_end > p_start)
  ORDER BY v.internal_code;
END$$

-- FR-TRP-1: operators available, licensed through the window, no overlap.
CREATE PROCEDURE sp_eligible_operators(IN p_start DATETIME, IN p_end DATETIME)
p: BEGIN
  SELECT e.id, e.name, e.phone, l.license_number, l.expiration_date
  FROM employees e
  JOIN licenses l ON l.id = e.license_id
  WHERE e.status = 'available'
    AND l.expiration_date >= DATE(p_end)
    AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.employee_id = e.id
        AND t.status IN ('scheduled','in_transit')
        AND t.planned_start < p_end AND t.planned_end > p_start)
  ORDER BY e.name;
END$$

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
  SELECT CONCAT(r.origin, ' -> ', r.destination) AS ruta,
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

-- FR-DASH: one row with the dashboard counters.
CREATE PROCEDURE sp_dashboard_alerts(IN p_today DATE,
    OUT p_expiring_licenses INT, OUT p_overdue_invoices INT,
    OUT p_maintenance_due INT, OUT p_pending_assignments INT)
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

-- Internal helper: like sp_validate_vehicle_assignment but appends to the
-- caller's problem string instead of resetting it (used by sp_assign_trip).
CREATE PROCEDURE validate_vehicle_assignment_into(IN p_request_id BIGINT,
    IN p_vehicle_id BIGINT, INOUT p_problems TEXT)
p: BEGIN
  DECLARE v_extra TEXT;
  CALL sp_validate_vehicle_assignment(p_request_id, p_vehicle_id, v_extra);
  IF v_extra IS NOT NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, v_extra);
  END IF;
END$$

CREATE PROCEDURE validate_operator_assignment_into(IN p_request_id BIGINT,
    IN p_operator_id BIGINT, INOUT p_problems TEXT)
p: BEGIN
  DECLARE v_extra TEXT;
  CALL sp_validate_operator_assignment(p_request_id, p_operator_id, v_extra);
  IF v_extra IS NOT NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, v_extra);
  END IF;
END$$

DELIMITER ;

-- ================================================================ app API
-- The application never writes SQL of its own: every repository operation
-- maps to one of these routines, so changing a routine changes the app.
-- Convention: read procedures return a result set; write procedures take their
-- parameters as IN and report through OUT (p_problems TEXT, p_id BIGINT).

DELIMITER $$

-- ---------------------------------------------------------------- validators

CREATE FUNCTION fn_money_valid(p_value DECIMAL(12,2))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_value IS NULL OR (p_value >= 0 AND p_value <= 9999999999.99));
END$$

CREATE FUNCTION fn_measure_valid(p_value DECIMAL(10,1))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_value IS NULL OR (p_value >= 0 AND p_value <= 999999999.9));
END$$

CREATE FUNCTION fn_liters_valid(p_value DECIMAL(8,2))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_value IS NOT NULL AND p_value > 0 AND p_value <= 999999.99);
END$$

CREATE FUNCTION fn_price_per_liter_valid(p_value DECIMAL(8,3))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_value IS NOT NULL AND p_value > 0 AND p_value <= 99999.999);
END$$

CREATE FUNCTION fn_date_valid(p_value DATE)
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_value IS NULL OR (p_value >= '2000-01-01' AND p_value <= '2100-01-01'));
END$$

CREATE FUNCTION fn_year_valid(p_year INT)
RETURNS TINYINT
READS SQL DATA
BEGIN
  RETURN (p_year IS NULL OR (p_year >= 1950 AND p_year <= YEAR(CURDATE()) + 1));
END$$

CREATE FUNCTION fn_curp_valid(p_curp VARCHAR(18))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_curp IS NULL OR p_curp = '' OR p_curp REGEXP '^[A-Z]{4}[0-9]{6}[HM][A-Z]{5}[A-Z0-9][0-9]$');
END$$

CREATE FUNCTION fn_plates_valid(p_plates VARCHAR(20))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_plates IS NULL OR p_plates = '' OR p_plates REGEXP '^[A-Z0-9-]{4,10}$');
END$$

CREATE FUNCTION fn_license_number_valid(p_number VARCHAR(50))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_number IS NULL OR p_number = '' OR p_number REGEXP '^[A-Za-z0-9-]{4,30}$');
END$$

CREATE FUNCTION fn_username_valid(p_username VARCHAR(50))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_username IS NOT NULL AND p_username REGEXP '^[A-Za-z0-9._-]{3,50}$');
END$$

-- ---------------------------------------------------------------- security

CREATE PROCEDURE sp_user_by_username(IN p_username VARCHAR(50))
p: BEGIN
  SELECT u.id, u.employee_id, u.username, u.password_hash, u.role_id,
         r.name AS role_name, u.status
  FROM users u JOIN roles r ON r.id = u.role_id
  WHERE u.username = p_username;
END$$

CREATE PROCEDURE sp_user_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT u.id, u.employee_id, u.username, u.password_hash, u.role_id,
         r.name AS role_name, u.status
  FROM users u JOIN roles r ON r.id = u.role_id
  WHERE u.id = p_id;
END$$

CREATE PROCEDURE sp_users_list()
p: BEGIN
  SELECT u.id, u.employee_id, u.username, u.password_hash, u.role_id,
         r.name AS role_name, u.status
  FROM users u JOIN roles r ON r.id = u.role_id
  ORDER BY u.username;
END$$

CREATE PROCEDURE sp_roles_list()
p: BEGIN
  SELECT id, name FROM roles ORDER BY name;
END$$

CREATE PROCEDURE sp_permissions_list()
p: BEGIN
  SELECT name FROM permissions ORDER BY name;
END$$

CREATE PROCEDURE sp_role_permissions(IN p_role_id BIGINT)
p: BEGIN
  SELECT p.name
  FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
  WHERE rp.role_id = p_role_id
  ORDER BY p.name;
END$$

CREATE PROCEDURE sp_user_insert(IN p_username VARCHAR(50), IN p_hash VARCHAR(100),
    IN p_role_id BIGINT, IN p_employee_id BIGINT, IN p_status VARCHAR(20), OUT p_id BIGINT)
p: BEGIN
  CALL sp_next_id('users', p_id);
  INSERT INTO users (id, username, password_hash, role_id, employee_id, status)
  VALUES (p_id, p_username, p_hash, p_role_id, p_employee_id, COALESCE(p_status, 'active'));
END$$

CREATE PROCEDURE sp_user_update(IN p_id BIGINT, IN p_username VARCHAR(50),
    IN p_role_id BIGINT, IN p_employee_id BIGINT, IN p_status VARCHAR(20))
p: BEGIN
  UPDATE users SET username = p_username, role_id = p_role_id,
                   employee_id = p_employee_id, status = COALESCE(p_status, 'active')
  WHERE id = p_id;
END$$

CREATE PROCEDURE sp_user_update_password(IN p_id BIGINT, IN p_hash VARCHAR(100))
p: BEGIN
  UPDATE users SET password_hash = p_hash WHERE id = p_id;
END$$

CREATE PROCEDURE sp_user_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM users WHERE id = p_id;
END$$

CREATE PROCEDURE sp_audit_log(IN p_user_id BIGINT, IN p_entity VARCHAR(50),
    IN p_entity_id BIGINT, IN p_action VARCHAR(50), IN p_details VARCHAR(500))
p: BEGIN
  DECLARE v_id BIGINT;
  CALL sp_next_id('audit_log', v_id);
  INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
  VALUES (v_id, p_user_id, p_entity, p_entity_id, p_action, p_details);
END$$

-- ---------------------------------------------------------------- clients

CREATE PROCEDURE sp_clients_search(IN p_term VARCHAR(150))
p: BEGIN
  SELECT id, name, rfc, address, phone, email, contact_name, client_type,
         payment_terms, credit_limit, credit_days, status
  FROM clients
  WHERE p_term IS NULL OR p_term = ''
     OR name LIKE CONCAT('%', p_term, '%') OR rfc LIKE CONCAT('%', p_term, '%')
  ORDER BY name;
END$$

CREATE PROCEDURE sp_clients_active()
p: BEGIN
  SELECT id, name, rfc, address, phone, email, contact_name, client_type,
         payment_terms, credit_limit, credit_days, status
  FROM clients WHERE status = 'active' ORDER BY name;
END$$

CREATE PROCEDURE sp_client_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT id, name, rfc, address, phone, email, contact_name, client_type,
         payment_terms, credit_limit, credit_days, status
  FROM clients WHERE id = p_id;
END$$

CREATE PROCEDURE sp_client_save(IN p_id BIGINT, IN p_name VARCHAR(150), IN p_rfc VARCHAR(13),
    IN p_address VARCHAR(255), IN p_phone VARCHAR(30), IN p_email VARCHAR(150),
    IN p_contact VARCHAR(150), IN p_type VARCHAR(20), IN p_terms VARCHAR(20),
    IN p_credit_limit DECIMAL(12,2), IN p_credit_days INT, IN p_status VARCHAR(20),
    OUT p_new_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_name IS NULL OR p_name = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El nombre o razon social es obligatorio');
  END IF;
  IF p_rfc IS NULL OR p_rfc = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El RFC es obligatorio');
  ELSEIF NOT fn_rfc_valid(p_rfc) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El RFC no tiene un formato valido (ej. ABC950101XYZ)');
  END IF;
  IF NOT fn_email_valid(p_email) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El correo electronico no tiene un formato valido');
  END IF;
  IF NOT fn_phone_valid(p_phone) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El telefono no tiene un formato valido');
  END IF;
  IF p_terms = 'credit' THEN
    IF p_credit_days IS NULL OR p_credit_days < 0 OR p_credit_days > 3650 THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'Los dias de credito deben estar entre 0 y 3650');
    END IF;
    IF NOT fn_money_valid(p_credit_limit) THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'El limite de credito es invalido o excede el maximo permitido');
    END IF;
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  IF p_id IS NULL OR p_id = 0 THEN
    CALL sp_next_id('clients', p_new_id);
    INSERT INTO clients (id, name, rfc, address, phone, email, contact_name, client_type,
                         payment_terms, credit_limit, credit_days, status)
    VALUES (p_new_id, p_name, p_rfc, p_address, p_phone, p_email, p_contact,
            COALESCE(p_type, 'occasional'), COALESCE(p_terms, 'cash'),
            COALESCE(p_credit_limit, 0), COALESCE(p_credit_days, 0), COALESCE(p_status, 'active'));
  ELSE
    SET p_new_id = p_id;
    UPDATE clients SET name = p_name, rfc = p_rfc, address = p_address, phone = p_phone,
                       email = p_email, contact_name = p_contact,
                       client_type = COALESCE(p_type, 'occasional'),
                       payment_terms = COALESCE(p_terms, 'cash'),
                       credit_limit = COALESCE(p_credit_limit, 0),
                       credit_days = COALESCE(p_credit_days, 0),
                       status = COALESCE(p_status, 'active')
    WHERE id = p_id;
  END IF;
END$$

CREATE PROCEDURE sp_client_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  DELETE FROM clients WHERE id = p_id;
END$$

CREATE PROCEDURE sp_client_set_status(IN p_id BIGINT, IN p_status VARCHAR(20))
p: BEGIN
  UPDATE clients SET status = p_status WHERE id = p_id;
END$$

CREATE PROCEDURE sp_client_rates_by_client(IN p_client_id BIGINT)
p: BEGIN
  SELECT cr.id, cr.client_id, cr.route_id,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         cr.rate, cr.valid_from, cr.valid_to
  FROM client_rates cr JOIN routes r ON r.id = cr.route_id
  WHERE cr.client_id = p_client_id
  ORDER BY r.origin, r.destination;
END$$

CREATE PROCEDURE sp_client_rate_save(IN p_id BIGINT, IN p_client_id BIGINT, IN p_route_id BIGINT,
    IN p_rate DECIMAL(12,2), IN p_valid_from DATE, IN p_valid_to DATE,
    OUT p_new_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_route_id IS NULL OR p_route_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar una ruta');
  END IF;
  IF p_rate IS NULL OR p_rate <= 0 OR NOT fn_money_valid(p_rate) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La tarifa debe ser mayor a cero y dentro del rango permitido');
  END IF;
  IF p_valid_from IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de vigencia inicial es obligatoria');
  END IF;
  IF p_valid_to IS NOT NULL AND p_valid_to < p_valid_from THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La vigencia final no puede ser anterior a la inicial');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  IF p_id IS NULL OR p_id = 0 THEN
    CALL sp_next_id('client_rates', p_new_id);
    INSERT INTO client_rates (id, client_id, route_id, rate, valid_from, valid_to)
    VALUES (p_new_id, p_client_id, p_route_id, p_rate, p_valid_from, p_valid_to);
  ELSE
    SET p_new_id = p_id;
    UPDATE client_rates SET route_id = p_route_id, rate = p_rate,
                            valid_from = p_valid_from, valid_to = p_valid_to
    WHERE id = p_id;
  END IF;
END$$

CREATE PROCEDURE sp_client_rate_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  DELETE FROM client_rates WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- routes

CREATE PROCEDURE sp_routes_search(IN p_term VARCHAR(150))
p: BEGIN
  SELECT id, origin, destination, estimated_km, description
  FROM routes
  WHERE p_term IS NULL OR p_term = ''
     OR origin LIKE CONCAT('%', p_term, '%') OR destination LIKE CONCAT('%', p_term, '%')
  ORDER BY origin, destination;
END$$

CREATE PROCEDURE sp_route_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT id, origin, destination, estimated_km, description FROM routes WHERE id = p_id;
END$$

CREATE PROCEDURE sp_route_save(IN p_id BIGINT, IN p_origin VARCHAR(150),
    IN p_destination VARCHAR(150), IN p_km DECIMAL(10,1), IN p_description VARCHAR(255),
    OUT p_new_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_origin IS NULL OR p_origin = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El origen es obligatorio');
  END IF;
  IF p_destination IS NULL OR p_destination = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El destino es obligatorio');
  END IF;
  IF NOT fn_measure_valid(p_km) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Los kilometros estimados son invalidos o exceden el maximo permitido');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  IF p_id IS NULL OR p_id = 0 THEN
    CALL sp_next_id('routes', p_new_id);
    INSERT INTO routes (id, origin, destination, estimated_km, description)
    VALUES (p_new_id, p_origin, p_destination, p_km, p_description);
  ELSE
    SET p_new_id = p_id;
    UPDATE routes SET origin = p_origin, destination = p_destination,
                      estimated_km = p_km, description = p_description
    WHERE id = p_id;
  END IF;
END$$

CREATE PROCEDURE sp_route_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  DELETE FROM routes WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- vehicles

CREATE PROCEDURE sp_vehicles_search(IN p_term VARCHAR(150))
p: BEGIN
  SELECT id, internal_code, plates, brand, model, year, serial_number, vehicle_type,
         load_capacity, mileage, status
  FROM vehicles
  WHERE p_term IS NULL OR p_term = ''
     OR internal_code LIKE CONCAT('%', p_term, '%') OR plates LIKE CONCAT('%', p_term, '%')
     OR brand LIKE CONCAT('%', p_term, '%') OR model LIKE CONCAT('%', p_term, '%')
  ORDER BY internal_code;
END$$

CREATE PROCEDURE sp_vehicle_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT id, internal_code, plates, brand, model, year, serial_number, vehicle_type,
         load_capacity, mileage, status
  FROM vehicles WHERE id = p_id;
END$$

CREATE PROCEDURE sp_vehicle_save(IN p_id BIGINT, IN p_code VARCHAR(30), IN p_plates VARCHAR(20),
    IN p_brand VARCHAR(50), IN p_model VARCHAR(50), IN p_year INT, IN p_serial VARCHAR(60),
    IN p_type VARCHAR(50), IN p_capacity DECIMAL(10,1), IN p_mileage DECIMAL(10,1),
    IN p_status VARCHAR(20), OUT p_new_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_code IS NULL OR p_code = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El numero economico es obligatorio');
  END IF;
  IF p_plates IS NULL OR p_plates = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Las placas son obligatorias');
  END IF;
  IF NOT fn_measure_valid(p_capacity) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La capacidad de carga es invalida o excede el maximo permitido');
  END IF;
  IF NOT fn_measure_valid(p_mileage) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El kilometraje es invalido o excede el maximo permitido');
  END IF;
  IF NOT fn_year_valid(p_year) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, CONCAT('El anio debe estar entre 1950 y ', YEAR(CURDATE()) + 1));
  END IF;
  IF NOT fn_plates_valid(p_plates) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Las placas no tienen un formato valido');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  IF p_id IS NULL OR p_id = 0 THEN
    CALL sp_next_id('vehicles', p_new_id);
    INSERT INTO vehicles (id, internal_code, plates, brand, model, year, serial_number,
                          vehicle_type, load_capacity, mileage, status)
    VALUES (p_new_id, p_code, p_plates, p_brand, p_model, p_year, p_serial, p_type,
            p_capacity, COALESCE(p_mileage, 0), COALESCE(p_status, 'available'));
  ELSE
    SET p_new_id = p_id;
    UPDATE vehicles SET internal_code = p_code, plates = p_plates, brand = p_brand,
                        model = p_model, year = p_year, serial_number = p_serial,
                        vehicle_type = p_type, load_capacity = p_capacity,
                        mileage = COALESCE(p_mileage, 0), status = COALESCE(p_status, 'available')
    WHERE id = p_id;
  END IF;
END$$

CREATE PROCEDURE sp_vehicle_set_status(IN p_id BIGINT, IN p_status VARCHAR(20))
p: BEGIN
  UPDATE vehicles SET status = p_status WHERE id = p_id;
END$$

CREATE PROCEDURE sp_vehicle_raise_mileage(IN p_id BIGINT, IN p_reading DECIMAL(10,1))
p: BEGIN
  IF p_reading IS NOT NULL THEN
    UPDATE vehicles SET mileage = p_reading WHERE id = p_id AND mileage < p_reading;
  END IF;
END$$

CREATE PROCEDURE sp_vehicle_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  DELETE FROM vehicles WHERE id = p_id;
END$$

CREATE PROCEDURE sp_eligible_vehicles_full(IN p_start DATETIME, IN p_end DATETIME)
p: BEGIN
  SELECT v.id, v.internal_code, v.plates, v.brand, v.model, v.year, v.serial_number,
         v.vehicle_type, v.load_capacity, v.mileage, v.status
  FROM vehicles v
  WHERE v.status = 'available'
    AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.vehicle_id = v.id
        AND t.status IN ('scheduled','in_transit')
        AND t.planned_start < p_end AND t.planned_end > p_start)
  ORDER BY v.internal_code;
END$$

-- ---------------------------------------------------------------- employees

CREATE PROCEDURE sp_employees_search(IN p_term VARCHAR(150))
p: BEGIN
  SELECT e.id, e.name, e.address, e.phone, e.email, e.rfc, e.curp,
         e.emergency_contact_name, e.emergency_contact_phone, e.status,
         l.id AS license_id, l.license_number, l.license_type, l.issue_date, l.expiration_date
  FROM employees e LEFT JOIN licenses l ON l.id = e.license_id
  WHERE p_term IS NULL OR p_term = '' OR e.name LIKE CONCAT('%', p_term, '%')
  ORDER BY e.name;
END$$

CREATE PROCEDURE sp_employee_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT e.id, e.name, e.address, e.phone, e.email, e.rfc, e.curp,
         e.emergency_contact_name, e.emergency_contact_phone, e.status,
         l.id AS license_id, l.license_number, l.license_type, l.issue_date, l.expiration_date
  FROM employees e LEFT JOIN licenses l ON l.id = e.license_id
  WHERE e.id = p_id;
END$$

CREATE PROCEDURE sp_employees_assignable()
p: BEGIN
  SELECT e.id, e.name, e.address, e.phone, e.email, e.rfc, e.curp,
         e.emergency_contact_name, e.emergency_contact_phone, e.status,
         l.id AS license_id, l.license_number, l.license_type, l.issue_date, l.expiration_date
  FROM employees e LEFT JOIN licenses l ON l.id = e.license_id
  WHERE e.status = 'available'
  ORDER BY e.name;
END$$

CREATE PROCEDURE sp_eligible_operators_full(IN p_start DATETIME, IN p_end DATETIME)
p: BEGIN
  SELECT e.id, e.name, e.address, e.phone, e.email, e.rfc, e.curp,
         e.emergency_contact_name, e.emergency_contact_phone, e.status,
         l.id AS license_id, l.license_number, l.license_type, l.issue_date, l.expiration_date
  FROM employees e JOIN licenses l ON l.id = e.license_id
  WHERE e.status = 'available'
    AND l.expiration_date >= DATE(p_end)
    AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.employee_id = e.id
        AND t.status IN ('scheduled','in_transit')
        AND t.planned_start < p_end AND t.planned_end > p_start)
  ORDER BY e.name;
END$$

CREATE PROCEDURE sp_employee_save(IN p_id BIGINT, IN p_name VARCHAR(150), IN p_address VARCHAR(255),
    IN p_phone VARCHAR(30), IN p_email VARCHAR(150), IN p_rfc VARCHAR(13), IN p_curp VARCHAR(18),
    IN p_ec_name VARCHAR(150), IN p_ec_phone VARCHAR(30), IN p_license_id BIGINT,
    IN p_license_number VARCHAR(50), IN p_license_type VARCHAR(50), IN p_license_issue DATE,
    IN p_license_expiry DATE, IN p_status VARCHAR(20),
    OUT p_new_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_license_id BIGINT DEFAULT NULL;
  SET p_problems = NULL;
  IF p_name IS NULL OR p_name = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El nombre es obligatorio');
  END IF;
  IF p_phone IS NULL OR p_phone = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El telefono es obligatorio');
  ELSEIF NOT fn_phone_valid(p_phone) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El telefono no tiene un formato valido');
  END IF;
  IF NOT fn_email_valid(p_email) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El correo electronico no tiene un formato valido');
  END IF;
  IF NOT fn_rfc_valid(p_rfc) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El RFC no tiene un formato valido');
  END IF;
  IF NOT fn_curp_valid(p_curp) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La CURP no tiene un formato valido');
  END IF;
  IF NOT fn_phone_valid(p_ec_phone) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El telefono de emergencia no tiene un formato valido');
  END IF;
  IF p_license_number IS NOT NULL AND p_license_number <> '' THEN
    IF NOT fn_license_number_valid(p_license_number) THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'El numero de licencia no tiene un formato valido');
    END IF;
    IF p_license_expiry IS NULL THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de vencimiento de la licencia es obligatoria');
    END IF;
    IF NOT fn_date_valid(p_license_issue) OR NOT fn_date_valid(p_license_expiry) THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'Las fechas de la licencia no son validas');
    ELSEIF p_license_issue IS NOT NULL AND p_license_issue > p_license_expiry THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de expedicion no puede ser posterior al vencimiento');
    END IF;
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  IF p_license_number IS NOT NULL AND p_license_number <> '' THEN
    IF p_license_id IS NULL OR p_license_id = 0 THEN
      CALL sp_next_id('licenses', v_license_id);
      INSERT INTO licenses (id, license_number, license_type, issue_date, expiration_date)
      VALUES (v_license_id, p_license_number, p_license_type, p_license_issue, p_license_expiry);
    ELSE
      SET v_license_id = p_license_id;
      UPDATE licenses SET license_number = p_license_number, license_type = p_license_type,
                          issue_date = p_license_issue, expiration_date = p_license_expiry
      WHERE id = p_license_id;
    END IF;
  END IF;

  IF p_id IS NULL OR p_id = 0 THEN
    CALL sp_next_id('employees', p_new_id);
    INSERT INTO employees (id, name, address, phone, email, rfc, curp,
                           emergency_contact_name, emergency_contact_phone, license_id, status)
    VALUES (p_new_id, p_name, p_address, p_phone, p_email, p_rfc, p_curp,
            p_ec_name, p_ec_phone, v_license_id, COALESCE(p_status, 'available'));
  ELSE
    SET p_new_id = p_id;
    UPDATE employees SET name = p_name, address = p_address, phone = p_phone, email = p_email,
                         rfc = p_rfc, curp = p_curp, emergency_contact_name = p_ec_name,
                         emergency_contact_phone = p_ec_phone, license_id = v_license_id,
                         status = COALESCE(p_status, 'available')
    WHERE id = p_id;
  END IF;
END$$

CREATE PROCEDURE sp_employee_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_license BIGINT DEFAULT NULL;
  SET p_problems = NULL;
  SELECT license_id INTO v_license FROM employees WHERE id = p_id;
  DELETE FROM employees WHERE id = p_id;
  IF v_license IS NOT NULL THEN
    DELETE FROM licenses WHERE id = v_license;
  END IF;
END$$

CREATE PROCEDURE sp_employee_set_status(IN p_id BIGINT, IN p_status VARCHAR(20))
p: BEGIN
  UPDATE employees SET status = p_status WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- requests

CREATE PROCEDURE sp_requests_search(IN p_folio VARCHAR(20), IN p_client_id BIGINT,
    IN p_status VARCHAR(20), IN p_from DATETIME, IN p_to DATETIME)
p: BEGIN
  SELECT sr.id, sr.folio, sr.client_id, c.name AS client_name, sr.route_id,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         sr.cargo_description, sr.estimated_weight, sr.pickup_date_scheduled,
         sr.delivery_date_scheduled, sr.agreed_rate, sr.requires_documents,
         sr.status, sr.notes, sr.created_at
  FROM service_requests sr
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  WHERE (p_folio IS NULL OR p_folio = '' OR sr.folio LIKE CONCAT('%', p_folio, '%'))
    AND (p_client_id IS NULL OR sr.client_id = p_client_id)
    AND (p_status IS NULL OR sr.status = p_status)
    AND (p_from IS NULL OR sr.pickup_date_scheduled >= p_from)
    AND (p_to IS NULL OR sr.pickup_date_scheduled <= p_to)
  ORDER BY sr.created_at DESC;
END$$

CREATE PROCEDURE sp_request_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT sr.id, sr.folio, sr.client_id, c.name AS client_name, sr.route_id,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         sr.cargo_description, sr.estimated_weight, sr.pickup_date_scheduled,
         sr.delivery_date_scheduled, sr.agreed_rate, sr.requires_documents,
         sr.status, sr.notes, sr.created_at
  FROM service_requests sr
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  WHERE sr.id = p_id;
END$$

CREATE PROCEDURE sp_requests_by_status(IN p_status VARCHAR(20))
p: BEGIN
  SELECT sr.id, sr.folio, sr.client_id, c.name AS client_name, sr.route_id,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         sr.cargo_description, sr.estimated_weight, sr.pickup_date_scheduled,
         sr.delivery_date_scheduled, sr.agreed_rate, sr.requires_documents,
         sr.status, sr.notes, sr.created_at
  FROM service_requests sr
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  WHERE sr.status = p_status
  ORDER BY sr.pickup_date_scheduled;
END$$

CREATE PROCEDURE sp_requests_authorized()
p: BEGIN
  SELECT sr.id, sr.folio, sr.client_id, c.name AS client_name, sr.route_id,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         sr.cargo_description, sr.estimated_weight, sr.pickup_date_scheduled,
         sr.delivery_date_scheduled, sr.agreed_rate, sr.requires_documents,
         sr.status, sr.notes, sr.created_at
  FROM service_requests sr
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  WHERE sr.status = 'authorized';
END$$

CREATE PROCEDURE sp_requests_pending_billing()
p: BEGIN
  SELECT sr.id, sr.folio, sr.client_id, c.name AS client_name, sr.route_id,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         sr.cargo_description, sr.estimated_weight, sr.pickup_date_scheduled,
         sr.delivery_date_scheduled, sr.agreed_rate, sr.requires_documents,
         sr.status, sr.notes, sr.created_at
  FROM service_requests sr
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  WHERE sr.agreed_rate IS NOT NULL AND sr.agreed_rate > 0
    AND sr.status <> 'cancelled'
    AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.service_request_id = sr.id)
  ORDER BY sr.created_at DESC;
END$$

CREATE PROCEDURE sp_request_create(IN p_client_id BIGINT, IN p_route_id BIGINT,
    IN p_cargo VARCHAR(255), IN p_weight DECIMAL(10,1), IN p_pickup DATETIME,
    IN p_delivery DATETIME, IN p_rate DECIMAL(12,2), IN p_requires_documents BOOLEAN,
    IN p_notes VARCHAR(500), IN p_user_id BIGINT, OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_year INT;
  DECLARE v_folio VARCHAR(20);
  SET p_problems = NULL;
  IF p_client_id IS NULL OR p_client_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un cliente');
  END IF;
  IF p_route_id IS NULL OR p_route_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar una ruta');
  END IF;
  IF NOT fn_measure_valid(p_weight) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El peso estimado es invalido o excede el maximo permitido');
  END IF;
  IF NOT fn_date_valid(DATE(p_pickup)) OR NOT fn_date_valid(DATE(p_delivery)) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Las fechas programadas no son validas');
  END IF;
  IF (p_pickup IS NULL) <> (p_delivery IS NULL) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe indicar ambas fechas o ninguna');
  ELSEIF p_pickup IS NOT NULL AND p_delivery <= p_pickup THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de entrega debe ser posterior a la de recoleccion');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  SET v_year = YEAR(COALESCE(p_pickup, CURDATE()));
  SET v_folio = fn_next_folio(v_year);
  CALL sp_next_id('service_requests', p_id);
  INSERT INTO service_requests
    (id, folio, client_id, route_id, cargo_description, estimated_weight,
     pickup_date_scheduled, delivery_date_scheduled, agreed_rate,
     requires_documents, status, notes, created_by, updated_by)
  VALUES (p_id, v_folio, p_client_id, p_route_id, p_cargo, p_weight, p_pickup, p_delivery,
          p_rate, COALESCE(p_requires_documents, TRUE), 'requested', p_notes, p_user_id, p_user_id);
END$$

CREATE PROCEDURE sp_request_update(IN p_id BIGINT, IN p_client_id BIGINT, IN p_route_id BIGINT,
    IN p_cargo VARCHAR(255), IN p_weight DECIMAL(10,1), IN p_pickup DATETIME,
    IN p_delivery DATETIME, IN p_rate DECIMAL(12,2), IN p_requires_documents BOOLEAN,
    IN p_status VARCHAR(20), IN p_notes VARCHAR(500), IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF NOT fn_measure_valid(p_weight) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El peso estimado es invalido o excede el maximo permitido');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  UPDATE service_requests SET client_id = p_client_id, route_id = p_route_id,
                              cargo_description = p_cargo, estimated_weight = p_weight,
                              pickup_date_scheduled = p_pickup, delivery_date_scheduled = p_delivery,
                              agreed_rate = p_rate, requires_documents = p_requires_documents,
                              status = p_status, notes = p_notes, updated_by = p_user_id
  WHERE id = p_id;
END$$

CREATE PROCEDURE sp_request_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  DELETE FROM payments WHERE invoice_id IN (SELECT id FROM invoices WHERE service_request_id = p_id);
  DELETE FROM invoices WHERE service_request_id = p_id;
  DELETE FROM expenses WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM advances WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM incidents WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM deliveries WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  UPDATE fuel_loads SET trip_id = NULL WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM audit_log WHERE entity = 'trip'
    AND entity_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM trips WHERE service_request_id = p_id;
  DELETE FROM audit_log WHERE entity = 'service_request' AND entity_id = p_id;
  DELETE FROM service_requests WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- trips

CREATE PROCEDURE sp_trips_search(IN p_term VARCHAR(150), IN p_status VARCHAR(20))
p: BEGIN
  SELECT t.id, t.service_request_id, sr.folio, c.name AS client_name,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         t.vehicle_id, CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label,
         t.employee_id, e.name AS employee_name,
         t.estimated_km, t.actual_km, t.planned_start, t.planned_end,
         t.departure_datetime, t.arrival_datetime, t.status
  FROM trips t
  JOIN service_requests sr ON sr.id = t.service_request_id
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  JOIN vehicles v ON v.id = t.vehicle_id
  JOIN employees e ON e.id = t.employee_id
  WHERE (p_term IS NULL OR p_term = ''
         OR sr.folio LIKE CONCAT('%', p_term, '%') OR c.name LIKE CONCAT('%', p_term, '%')
         OR v.internal_code LIKE CONCAT('%', p_term, '%') OR e.name LIKE CONCAT('%', p_term, '%'))
    AND (p_status IS NULL OR t.status = p_status)
  ORDER BY t.planned_start DESC;
END$$

CREATE PROCEDURE sp_trip_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT t.id, t.service_request_id, sr.folio, c.name AS client_name,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         t.vehicle_id, CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label,
         t.employee_id, e.name AS employee_name,
         t.estimated_km, t.actual_km, t.planned_start, t.planned_end,
         t.departure_datetime, t.arrival_datetime, t.status
  FROM trips t
  JOIN service_requests sr ON sr.id = t.service_request_id
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  JOIN vehicles v ON v.id = t.vehicle_id
  JOIN employees e ON e.id = t.employee_id
  WHERE t.id = p_id;
END$$

CREATE PROCEDURE sp_trip_by_request(IN p_request_id BIGINT)
p: BEGIN
  SELECT t.id, t.service_request_id, sr.folio, c.name AS client_name,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         t.vehicle_id, CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label,
         t.employee_id, e.name AS employee_name,
         t.estimated_km, t.actual_km, t.planned_start, t.planned_end,
         t.departure_datetime, t.arrival_datetime, t.status
  FROM trips t
  JOIN service_requests sr ON sr.id = t.service_request_id
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  JOIN vehicles v ON v.id = t.vehicle_id
  JOIN employees e ON e.id = t.employee_id
  WHERE t.service_request_id = p_request_id;
END$$

CREATE PROCEDURE sp_trips_due_to_depart(IN p_now DATETIME)
p: BEGIN
  SELECT t.id, t.service_request_id, sr.folio, c.name AS client_name,
         CONCAT(r.origin, ' -> ', r.destination) AS route_label,
         t.vehicle_id, CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label,
         t.employee_id, e.name AS employee_name,
         t.estimated_km, t.actual_km, t.planned_start, t.planned_end,
         t.departure_datetime, t.arrival_datetime, t.status
  FROM trips t
  JOIN service_requests sr ON sr.id = t.service_request_id
  JOIN clients c ON c.id = sr.client_id
  JOIN routes r ON r.id = sr.route_id
  JOIN vehicles v ON v.id = t.vehicle_id
  JOIN employees e ON e.id = t.employee_id
  WHERE t.status = 'scheduled' AND sr.status = 'assigned'
    AND t.planned_start IS NOT NULL AND t.planned_start <= p_now
  ORDER BY t.planned_start;
END$$

CREATE PROCEDURE sp_trip_vehicle(IN p_trip_id BIGINT)
p: BEGIN
  SELECT vehicle_id FROM trips WHERE id = p_trip_id;
END$$

CREATE PROCEDURE sp_reassign_trip(IN p_trip_id BIGINT, IN p_vehicle_id BIGINT,
    IN p_operator_id BIGINT, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_start DATETIME DEFAULT NULL;
  DECLARE v_end DATETIME DEFAULT NULL;
  DECLARE v_weight DECIMAL(10,1) DEFAULT NULL;
  DECLARE v_request BIGINT;
  DECLARE v_veh_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_capacity DECIMAL(10,1) DEFAULT NULL;
  DECLARE v_emp_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_exp DATE DEFAULT NULL;
  DECLARE v_conflicts INT DEFAULT 0;
  DECLARE v_audit_id BIGINT;

  SET p_problems = NULL;
  SELECT status, service_request_id INTO v_status, v_request FROM trips WHERE id = p_trip_id;
  IF v_status IS NULL THEN
    SET p_problems = 'Viaje no encontrado';
    LEAVE p;
  END IF;
  IF v_status <> 'scheduled' THEN
    SET p_problems = 'Solo se puede reasignar un viaje programado (aun no inicia)';
    LEAVE p;
  END IF;
  SELECT pickup_date_scheduled, delivery_date_scheduled, estimated_weight
    INTO v_start, v_end, v_weight
    FROM service_requests WHERE id = v_request;

  SELECT status, load_capacity INTO v_veh_status, v_capacity FROM vehicles WHERE id = p_vehicle_id;
  IF v_veh_status IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar una unidad');
  ELSE
    IF NOT fn_vehicle_assignable(v_veh_status) THEN
      SET p_problems = CONCAT_WS('; ', p_problems, CONCAT('La unidad no esta disponible (', v_veh_status, ')'));
    END IF;
    IF NOT fn_capacity_ok(v_capacity, v_weight) THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'La capacidad de la unidad es menor al peso estimado');
    END IF;
    SELECT COUNT(*) INTO v_conflicts FROM trips
      WHERE vehicle_id = p_vehicle_id AND id <> p_trip_id
        AND status IN ('scheduled','in_transit')
        AND planned_start < v_end AND planned_end > v_start;
    IF v_conflicts > 0 THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'La unidad ya tiene un viaje en ese periodo');
    END IF;
  END IF;

  SELECT e.status, l.expiration_date INTO v_emp_status, v_exp
    FROM employees e LEFT JOIN licenses l ON l.id = e.license_id WHERE e.id = p_operator_id;
  IF v_emp_status IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un operador');
  ELSE
    IF NOT fn_employee_assignable(v_emp_status) THEN
      SET p_problems = CONCAT_WS('; ', p_problems, CONCAT('El operador no esta disponible (', v_emp_status, ')'));
    END IF;
    IF v_exp IS NULL THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'El operador no tiene licencia registrada');
    ELSEIF v_end IS NOT NULL AND v_exp < DATE(v_end) THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'La licencia del operador vence antes del fin del viaje');
    END IF;
    SELECT COUNT(*) INTO v_conflicts FROM trips
      WHERE employee_id = p_operator_id AND id <> p_trip_id
        AND status IN ('scheduled','in_transit')
        AND planned_start < v_end AND planned_end > v_start;
    IF v_conflicts > 0 THEN
      SET p_problems = CONCAT_WS('; ', p_problems, 'El operador ya tiene un viaje en ese periodo');
    END IF;
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  UPDATE trips SET vehicle_id = p_vehicle_id, employee_id = p_operator_id, updated_by = p_user_id
  WHERE id = p_trip_id;
  CALL sp_next_id('audit_log', v_audit_id);
  INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
  VALUES (v_audit_id, p_user_id, 'trip', p_trip_id, 'reassigned',
          CONCAT('vehicle=', p_vehicle_id, ', operator=', p_operator_id));
END$$

CREATE PROCEDURE sp_trip_delete(IN p_trip_id BIGINT, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_request BIGINT;
  SET p_problems = NULL;
  SELECT status, service_request_id INTO v_status, v_request FROM trips WHERE id = p_trip_id;
  IF v_status IS NULL THEN
    SET p_problems = 'Viaje no encontrado';
    LEAVE p;
  END IF;
  DELETE FROM expenses WHERE trip_id = p_trip_id;
  DELETE FROM advances WHERE trip_id = p_trip_id;
  DELETE FROM incidents WHERE trip_id = p_trip_id;
  DELETE FROM deliveries WHERE trip_id = p_trip_id;
  UPDATE fuel_loads SET trip_id = NULL WHERE trip_id = p_trip_id;
  DELETE FROM audit_log WHERE entity = 'trip' AND entity_id = p_trip_id;
  DELETE FROM trips WHERE id = p_trip_id;
  UPDATE service_requests SET status = 'scheduled', updated_by = p_user_id
  WHERE id = v_request AND status IN ('assigned', 'in_transit');
END$$

CREATE PROCEDURE sp_trip_set_status(IN p_id BIGINT, IN p_status VARCHAR(20), IN p_user_id BIGINT)
p: BEGIN
  UPDATE trips SET status = p_status, updated_by = p_user_id WHERE id = p_id;
END$$

CREATE PROCEDURE sp_sweep_lifecycle(IN p_user_id BIGINT, OUT p_changes INT)
p: BEGIN
  DECLARE done INT DEFAULT 0;
  DECLARE v_trip BIGINT;
  DECLARE v_vehicle BIGINT;
  DECLARE v_employee BIGINT;
  DECLARE v_request BIGINT;
  DECLARE v_audit BIGINT;
  DECLARE v_now DATETIME;
  DECLARE v_scheduled INT DEFAULT 0;
  DECLARE cur CURSOR FOR
    SELECT t.id, t.vehicle_id, t.employee_id, t.service_request_id
    FROM trips t JOIN service_requests sr ON sr.id = t.service_request_id
    WHERE t.status = 'scheduled' AND sr.status = 'assigned'
      AND t.planned_start IS NOT NULL AND t.planned_start <= NOW();
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

  SET v_now = NOW();
  UPDATE service_requests SET status = 'scheduled', updated_by = p_user_id
  WHERE status = 'authorized' AND pickup_date_scheduled IS NOT NULL
    AND delivery_date_scheduled IS NOT NULL
    AND delivery_date_scheduled > pickup_date_scheduled;
  SET v_scheduled = ROW_COUNT();

  SET p_changes = 0;
  OPEN cur;
  depart_loop: LOOP
    FETCH cur INTO v_trip, v_vehicle, v_employee, v_request;
    IF done = 1 THEN LEAVE depart_loop; END IF;
    UPDATE trips SET departure_datetime = v_now, status = 'in_transit', updated_by = p_user_id
    WHERE id = v_trip;
    UPDATE vehicles SET status = 'on_trip' WHERE id = v_vehicle;
    UPDATE employees SET status = 'on_trip' WHERE id = v_employee;
    UPDATE service_requests SET status = 'in_transit', updated_by = p_user_id WHERE id = v_request;
    CALL sp_next_id('audit_log', v_audit);
    INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
    VALUES (v_audit, p_user_id, 'trip', v_trip, 'departed', 'auto: planned start reached');
    SET p_changes = p_changes + 1;
  END LOOP;
  CLOSE cur;

  SET p_changes = p_changes + v_scheduled;
END$$

-- ---------------------------------------------------------------- deliveries

CREATE PROCEDURE sp_delivery_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT d.id, d.trip_id, sr.folio, d.actual_datetime, d.received_by,
         d.evidence_reference, d.status
  FROM deliveries d
  JOIN trips t ON t.id = d.trip_id
  JOIN service_requests sr ON sr.id = t.service_request_id
  WHERE d.trip_id = p_trip_id;
END$$

CREATE PROCEDURE sp_delivery_save(IN p_trip_id BIGINT, IN p_actual DATETIME,
    IN p_received_by VARCHAR(150), IN p_evidence VARCHAR(255), IN p_status VARCHAR(20),
    IN p_user_id BIGINT, OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_request BIGINT;
  DECLARE v_req_status VARCHAR(20);
  DECLARE v_existing BIGINT;
  SET p_problems = NULL;
  IF p_trip_id IS NULL OR p_trip_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un viaje');
  END IF;
  IF p_actual IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha y hora de entrega son obligatorias');
  END IF;
  IF p_received_by IS NULL OR p_received_by = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe indicar quien recibio la mercancia');
  END IF;
  IF p_evidence IS NULL OR p_evidence = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La referencia de evidencia es obligatoria');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  SELECT id INTO v_existing FROM deliveries WHERE trip_id = p_trip_id;
  IF v_existing IS NULL THEN
    CALL sp_next_id('deliveries', p_id);
    INSERT INTO deliveries (id, trip_id, actual_datetime, received_by, evidence_reference, status, created_by)
    VALUES (p_id, p_trip_id, p_actual, p_received_by, p_evidence, COALESCE(p_status, 'complete'), p_user_id);
  ELSE
    SET p_id = v_existing;
    UPDATE deliveries SET actual_datetime = p_actual, received_by = p_received_by,
                          evidence_reference = p_evidence, status = COALESCE(p_status, 'complete')
    WHERE trip_id = p_trip_id;
  END IF;

  SELECT sr.id, sr.status INTO v_request, v_req_status
    FROM trips t JOIN service_requests sr ON sr.id = t.service_request_id
    WHERE t.id = p_trip_id;
  IF v_req_status = 'in_transit' THEN
    UPDATE service_requests SET status = 'delivered', updated_by = p_user_id WHERE id = v_request;
  END IF;
END$$

CREATE PROCEDURE sp_delivery_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM deliveries WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- incidents

CREATE PROCEDURE sp_incidents_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT i.id, i.trip_id, sr.folio, i.incident_date, i.incident_time,
         i.location, i.incident_type, i.description, i.actions_taken
  FROM incidents i
  JOIN trips t ON t.id = i.trip_id
  JOIN service_requests sr ON sr.id = t.service_request_id
  WHERE i.trip_id = p_trip_id
  ORDER BY i.incident_date, i.incident_time;
END$$

CREATE PROCEDURE sp_incident_save(IN p_trip_id BIGINT, IN p_date DATE, IN p_time TIME,
    IN p_location VARCHAR(150), IN p_type VARCHAR(30), IN p_description VARCHAR(500),
    IN p_actions VARCHAR(500), IN p_user_id BIGINT, OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_trip_id IS NULL OR p_trip_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar un viaje');
  END IF;
  IF p_date IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de la incidencia es obligatoria');
  ELSEIF NOT fn_date_valid(p_date) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de la incidencia no es valida');
  END IF;
  IF p_description IS NULL OR p_description = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La descripcion es obligatoria');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  CALL sp_next_id('incidents', p_id);
  INSERT INTO incidents (id, trip_id, incident_date, incident_time, location, incident_type,
                         description, actions_taken, created_by)
  VALUES (p_id, p_trip_id, p_date, p_time, p_location, p_type, p_description, p_actions, p_user_id);
END$$

CREATE PROCEDURE sp_incident_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM incidents WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- expenses

CREATE PROCEDURE sp_expenses_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT e.id, e.trip_id, sr.folio, e.expense_type, e.amount, e.expense_date, e.description
  FROM expenses e
  JOIN trips t ON t.id = e.trip_id
  JOIN service_requests sr ON sr.id = t.service_request_id
  WHERE e.trip_id = p_trip_id
  ORDER BY e.expense_date;
END$$

CREATE PROCEDURE sp_expenses_list()
p: BEGIN
  SELECT e.id, e.trip_id, sr.folio, e.expense_type, e.amount, e.expense_date, e.description
  FROM expenses e
  JOIN trips t ON t.id = e.trip_id
  JOIN service_requests sr ON sr.id = t.service_request_id
  ORDER BY e.expense_date DESC;
END$$

CREATE PROCEDURE sp_expense_sum_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT COALESCE(SUM(amount), 0) AS total FROM expenses WHERE trip_id = p_trip_id;
END$$

CREATE PROCEDURE sp_expense_save(IN p_trip_id BIGINT, IN p_type VARCHAR(20),
    IN p_amount DECIMAL(12,2), IN p_date DATE, IN p_description VARCHAR(255),
    IN p_user_id BIGINT, OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_extra TEXT;
  CALL sp_validate_expense(p_trip_id, p_type, p_amount, p_date, p_problems);
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  IF NOT fn_date_valid(p_date) THEN
    SET p_problems = 'La fecha del gasto no es valida';
    LEAVE p;
  END IF;
  CALL sp_next_id('expenses', p_id);
  INSERT INTO expenses (id, trip_id, expense_type, amount, expense_date, description, created_by)
  VALUES (p_id, p_trip_id, p_type, p_amount, p_date, p_description, p_user_id);
END$$

CREATE PROCEDURE sp_expense_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM expenses WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- advances

CREATE PROCEDURE sp_advances_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT a.id, a.trip_id, sr.folio, a.employee_id, e.name AS employee_name,
         a.amount_given, a.delivered_date, a.status, a.settled_at
  FROM advances a
  JOIN trips t ON t.id = a.trip_id
  JOIN service_requests sr ON sr.id = t.service_request_id
  JOIN employees e ON e.id = a.employee_id
  WHERE a.trip_id = p_trip_id
  ORDER BY a.delivered_date;
END$$

CREATE PROCEDURE sp_advances_list()
p: BEGIN
  SELECT a.id, a.trip_id, sr.folio, a.employee_id, e.name AS employee_name,
         a.amount_given, a.delivered_date, a.status, a.settled_at
  FROM advances a
  JOIN trips t ON t.id = a.trip_id
  JOIN service_requests sr ON sr.id = t.service_request_id
  JOIN employees e ON e.id = a.employee_id
  ORDER BY a.delivered_date DESC;
END$$

CREATE PROCEDURE sp_advance_save(IN p_trip_id BIGINT, IN p_employee_id BIGINT,
    IN p_amount DECIMAL(12,2), IN p_date DATE, IN p_user_id BIGINT,
    OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  CALL sp_validate_advance(p_trip_id, p_employee_id, p_amount, p_date, p_problems);
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  IF NOT fn_date_valid(p_date) THEN
    SET p_problems = 'La fecha del anticipo no es valida';
    LEAVE p;
  END IF;
  CALL sp_next_id('advances', p_id);
  INSERT INTO advances (id, trip_id, employee_id, amount_given, delivered_date, status, created_by)
  VALUES (p_id, p_trip_id, p_employee_id, p_amount, p_date, 'pending', p_user_id);
END$$

CREATE PROCEDURE sp_advance_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM advances WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- fuel

CREATE PROCEDURE sp_fuel_by_vehicle(IN p_vehicle_id BIGINT)
p: BEGIN
  SELECT f.id, f.vehicle_id, f.trip_id, f.fuel_station, f.load_date, f.liters,
         f.price_per_liter, f.amount, f.odometer_reading,
         CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label, sr.folio
  FROM fuel_loads f
  JOIN vehicles v ON v.id = f.vehicle_id
  LEFT JOIN trips t ON t.id = f.trip_id
  LEFT JOIN service_requests sr ON sr.id = t.service_request_id
  WHERE f.vehicle_id = p_vehicle_id
  ORDER BY f.load_date DESC;
END$$

CREATE PROCEDURE sp_fuel_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT f.id, f.vehicle_id, f.trip_id, f.fuel_station, f.load_date, f.liters,
         f.price_per_liter, f.amount, f.odometer_reading,
         CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label, sr.folio
  FROM fuel_loads f
  JOIN vehicles v ON v.id = f.vehicle_id
  LEFT JOIN trips t ON t.id = f.trip_id
  LEFT JOIN service_requests sr ON sr.id = t.service_request_id
  WHERE f.trip_id = p_trip_id
  ORDER BY f.load_date;
END$$

CREATE PROCEDURE sp_fuel_list()
p: BEGIN
  SELECT f.id, f.vehicle_id, f.trip_id, f.fuel_station, f.load_date, f.liters,
         f.price_per_liter, f.amount, f.odometer_reading,
         CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label, sr.folio
  FROM fuel_loads f
  JOIN vehicles v ON v.id = f.vehicle_id
  LEFT JOIN trips t ON t.id = f.trip_id
  LEFT JOIN service_requests sr ON sr.id = t.service_request_id
  ORDER BY f.load_date DESC;
END$$

CREATE PROCEDURE sp_fuel_sum_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT COALESCE(SUM(amount), 0) AS total FROM fuel_loads WHERE trip_id = p_trip_id;
END$$

CREATE PROCEDURE sp_fuel_save(IN p_vehicle_id BIGINT, IN p_trip_id BIGINT, IN p_station VARCHAR(100),
    IN p_load_date DATETIME, IN p_liters DECIMAL(8,2), IN p_price DECIMAL(8,3),
    IN p_amount DECIMAL(12,2), IN p_odometer DECIMAL(10,1), IN p_user_id BIGINT,
    OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  CALL sp_validate_fuel_load(p_vehicle_id, p_trip_id, p_liters, p_price, p_amount, p_odometer, p_problems);
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  CALL sp_next_id('fuel_loads', p_id);
  INSERT INTO fuel_loads (id, vehicle_id, trip_id, fuel_station, load_date, liters,
                          price_per_liter, amount, odometer_reading, created_by)
  VALUES (p_id, p_vehicle_id, p_trip_id, p_station, p_load_date, p_liters, p_price,
          p_amount, p_odometer, p_user_id);
  IF p_odometer IS NOT NULL THEN
    UPDATE vehicles SET mileage = p_odometer WHERE id = p_vehicle_id AND mileage < p_odometer;
  END IF;
END$$

CREATE PROCEDURE sp_fuel_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM fuel_loads WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- maintenance

CREATE PROCEDURE sp_maintenance_by_vehicle(IN p_vehicle_id BIGINT)
p: BEGIN
  SELECT m.id, m.vehicle_id, m.maintenance_date, m.odometer_reading, m.maintenance_type,
         m.work_performed, m.provider, m.cost, m.next_service_date, m.next_service_km,
         CONCAT(v.internal_code, ' (', v.plates, ')') AS vehicle_label
  FROM maintenance m JOIN vehicles v ON v.id = m.vehicle_id
  WHERE m.vehicle_id = p_vehicle_id
  ORDER BY m.maintenance_date DESC;
END$$

CREATE PROCEDURE sp_maintenance_save(IN p_vehicle_id BIGINT, IN p_date DATE,
    IN p_odometer DECIMAL(10,1), IN p_type VARCHAR(20), IN p_work VARCHAR(500),
    IN p_provider VARCHAR(150), IN p_cost DECIMAL(12,2), IN p_next_date DATE,
    IN p_next_km DECIMAL(10,1), IN p_user_id BIGINT, OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_vehicle_id IS NULL OR p_vehicle_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar una unidad');
  END IF;
  IF p_date IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de mantenimiento es obligatoria');
  ELSEIF NOT fn_date_valid(p_date) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de mantenimiento no es valida');
  END IF;
  IF NOT fn_money_valid(p_cost) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El costo es invalido o excede el maximo permitido');
  END IF;
  IF NOT fn_measure_valid(p_odometer) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El odometro es invalido o excede el maximo permitido');
  END IF;
  IF NOT fn_measure_valid(p_next_km) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El proximo kilometraje es invalido o excede el maximo permitido');
  END IF;
  IF p_next_date IS NOT NULL AND p_date IS NOT NULL AND p_next_date < p_date THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La proxima fecha de servicio no puede ser anterior a la del mantenimiento');
  END IF;
  IF p_next_km IS NOT NULL AND p_odometer IS NOT NULL AND p_next_km < p_odometer THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El proximo kilometraje no puede ser menor al odometro actual');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  CALL sp_next_id('maintenance', p_id);
  INSERT INTO maintenance (id, vehicle_id, maintenance_date, odometer_reading, maintenance_type,
                           work_performed, provider, cost, next_service_date, next_service_km, created_by)
  VALUES (p_id, p_vehicle_id, p_date, p_odometer, COALESCE(p_type, 'preventive'), p_work,
          p_provider, COALESCE(p_cost, 0), p_next_date, p_next_km, p_user_id);
  IF p_odometer IS NOT NULL THEN
    UPDATE vehicles SET mileage = p_odometer WHERE id = p_vehicle_id AND mileage < p_odometer;
  END IF;
END$$

CREATE PROCEDURE sp_maintenance_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM maintenance WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- invoices

CREATE PROCEDURE sp_invoices_search(IN p_status VARCHAR(20), IN p_client_id BIGINT)
p: BEGIN
  SELECT i.id, i.client_id, c.name AS client_name, i.service_request_id, sr.folio,
         i.invoice_number, i.amount, i.issue_date, i.due_date, i.status,
         COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0) AS paid
  FROM invoices i
  JOIN clients c ON c.id = i.client_id
  JOIN service_requests sr ON sr.id = i.service_request_id
  WHERE (p_status IS NULL OR i.status = p_status)
    AND (p_client_id IS NULL OR i.client_id = p_client_id)
  ORDER BY i.issue_date DESC;
END$$

CREATE PROCEDURE sp_invoice_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT i.id, i.client_id, c.name AS client_name, i.service_request_id, sr.folio,
         i.invoice_number, i.amount, i.issue_date, i.due_date, i.status,
         COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0) AS paid
  FROM invoices i
  JOIN clients c ON c.id = i.client_id
  JOIN service_requests sr ON sr.id = i.service_request_id
  WHERE i.id = p_id;
END$$

CREATE PROCEDURE sp_invoice_by_request(IN p_request_id BIGINT)
p: BEGIN
  SELECT i.id, i.client_id, c.name AS client_name, i.service_request_id, sr.folio,
         i.invoice_number, i.amount, i.issue_date, i.due_date, i.status,
         COALESCE((SELECT SUM(p.amount) FROM payments p WHERE p.invoice_id = i.id), 0) AS paid
  FROM invoices i
  JOIN clients c ON c.id = i.client_id
  JOIN service_requests sr ON sr.id = i.service_request_id
  WHERE i.service_request_id = p_request_id;
END$$

CREATE PROCEDURE sp_invoice_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  DELETE FROM payments WHERE invoice_id = p_id;
  DELETE FROM invoices WHERE id = p_id;
END$$

CREATE PROCEDURE sp_invoice_set_status(IN p_id BIGINT, IN p_status VARCHAR(20))
p: BEGIN
  UPDATE invoices SET status = p_status WHERE id = p_id;
END$$

-- ---------------------------------------------------------------- payments

CREATE PROCEDURE sp_payments_by_invoice(IN p_invoice_id BIGINT)
p: BEGIN
  SELECT p.id, p.invoice_id, i.invoice_number, p.amount, p.payment_date, p.payment_method
  FROM payments p JOIN invoices i ON i.id = p.invoice_id
  WHERE p.invoice_id = p_invoice_id
  ORDER BY p.payment_date;
END$$

CREATE PROCEDURE sp_payments_list()
p: BEGIN
  SELECT p.id, p.invoice_id, i.invoice_number, p.amount, p.payment_date, p.payment_method
  FROM payments p JOIN invoices i ON i.id = p.invoice_id
  ORDER BY p.payment_date DESC;
END$$

CREATE PROCEDURE sp_payment_sum_by_invoice(IN p_invoice_id BIGINT)
p: BEGIN
  SELECT COALESCE(SUM(amount), 0) AS total FROM payments WHERE invoice_id = p_invoice_id;
END$$

CREATE PROCEDURE sp_payment_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM payments WHERE id = p_id;
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

-- ================================================================ extras

DELIMITER $$

-- BR-04: newest rate valid for the client/route on the given date.
CREATE PROCEDURE sp_client_rate_suggest(IN p_client_id BIGINT, IN p_route_id BIGINT,
    IN p_on_date DATE)
p: BEGIN
  SELECT cr.rate
  FROM client_rates cr
  WHERE cr.client_id = p_client_id AND cr.route_id = p_route_id
    AND cr.valid_from <= p_on_date
    AND (cr.valid_to IS NULL OR cr.valid_to >= p_on_date)
  ORDER BY cr.valid_from DESC
  LIMIT 1;
END$$

-- BR-13 / FR-DEL-2 is implemented by sp_close_request above.

DELIMITER ;
