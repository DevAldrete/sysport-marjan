-- SysPort - MARJAN :: schema
-- MariaDB 11. English status codes, BIGINT auto-increment ids, DECIMAL money.
-- Design notes: the child column always references the parent id (PRD 4.1 F1).
-- Foreign keys are declared inline so table creation order is self-documenting.

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
  IF EXISTS (SELECT 1 FROM invoices WHERE service_request_id = p_request_id) THEN
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
