-- ================================================================ routines
-- Shared rule functions and the id/folio allocators.
-- The database is the single source of truth for these rules (see PRD section 6).

USE sysportdb;

DELIMITER $$

-- ================================================================ rules
-- The database is the single source of truth for the application's business
-- rules. This section holds the rule functions, the validation routines and
-- the transactional action procedures that the repositories call. Java keeps
-- only records, enums and thin JDBC wrappers, so a rule change here changes
-- the app without recompiling.
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

-- BR-03: a request can (re)schedule its dates while it has not started:
-- authorized (first schedule), scheduled (typo fix) and assigned (before the
-- trip departs). in_transit and later are frozen.
CREATE FUNCTION fn_request_reschedulable(p_status VARCHAR(20))
RETURNS TINYINT DETERMINISTIC
BEGIN
  RETURN (p_status IN ('authorized','scheduled','assigned'));
END$$

-- BR-08: effective weight of a request = sum of its packages
-- (quantity x unit weight) or, when it has none, the manually estimated weight.
CREATE FUNCTION fn_request_weight(p_request_id BIGINT)
RETURNS DECIMAL(10,1)
READS SQL DATA
BEGIN
  DECLARE v_packages DECIMAL(12,2) DEFAULT NULL;
  SELECT SUM(p.quantity * p.unit_weight) INTO v_packages
    FROM request_packages p WHERE p.service_request_id = p_request_id;
  IF v_packages IS NOT NULL THEN
    RETURN v_packages;
  END IF;
  RETURN (SELECT estimated_weight FROM service_requests WHERE id = p_request_id);
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
-- Allocates the next id for a table. The common path is a single atomic
-- UPDATE whose value is read back through LAST_INSERT_ID(), so two sessions
-- can never receive the same id (the old UPDATE-then-SELECT could).
CREATE PROCEDURE sp_next_id(IN p_name VARCHAR(50), OUT p_id BIGINT)
p: BEGIN
  DECLARE v_exists INT DEFAULT 0;
  SELECT COUNT(*) INTO v_exists FROM sequences WHERE name = p_name;
  IF v_exists = 0 THEN
    -- First use of an unprimed sequence: continue after the table's max id.
    SET @seq_table := p_name;
    SET @seq_max := 0;
    SET @seq_sql := CONCAT('SELECT COALESCE(MAX(id),0) INTO @seq_max FROM `', @seq_table, '`');
    PREPARE seq_stmt FROM @seq_sql;
    EXECUTE seq_stmt;
    DEALLOCATE PREPARE seq_stmt;
    INSERT INTO sequences(name, next_value) VALUES (p_name, @seq_max + 1);
    SET p_id = @seq_max + 1;
  ELSE
    UPDATE sequences SET next_value = LAST_INSERT_ID(next_value + 1) WHERE name = p_name;
    SET p_id = LAST_INSERT_ID();
  END IF;
END$$

-- ================================================================ app API
-- The application never writes SQL of its own: every repository operation
-- maps to one of these routines, so changing a routine changes the app.
-- Convention: read procedures return a result set; write procedures take their
-- parameters as IN and report through OUT (p_problems TEXT, p_id BIGINT).


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

DELIMITER ;
