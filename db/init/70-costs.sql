-- ================================================================ routines
-- Expenses and operator advances with settlement (BR-16/17).

USE sysportdb;

DELIMITER $$

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
  IF p_amount IS NULL OR p_amount <= 0 OR NOT fn_money_valid(p_amount) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El importe debe ser mayor a cero y dentro del rango permitido');
  END IF;
  IF p_date IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha del gasto es obligatoria');
  ELSEIF NOT fn_date_valid(p_date) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha del gasto no es valida');
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
  IF p_amount IS NULL OR p_amount <= 0 OR NOT fn_money_valid(p_amount) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El monto del anticipo debe ser mayor a cero y dentro del rango permitido');
  END IF;
  IF p_date IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de entrega del anticipo es obligatoria');
  ELSEIF NOT fn_date_valid(p_date) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha del anticipo no es valida');
  END IF;
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

CREATE PROCEDURE sp_expense_save(IN p_trip_id BIGINT, IN p_type VARCHAR(20),
    IN p_amount DECIMAL(12,2), IN p_date DATE, IN p_description VARCHAR(255),
    IN p_user_id BIGINT, OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_extra TEXT;
  CALL sp_validate_expense(p_trip_id, p_type, p_amount, p_date, p_problems);
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
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

CREATE PROCEDURE sp_advance_save(IN p_trip_id BIGINT, IN p_employee_id BIGINT,
    IN p_amount DECIMAL(12,2), IN p_date DATE, IN p_user_id BIGINT,
    OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  CALL sp_validate_advance(p_trip_id, p_employee_id, p_amount, p_date, p_problems);
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  CALL sp_next_id('advances', p_id);
  INSERT INTO advances (id, trip_id, employee_id, amount_given, delivered_date, status, created_by)
  VALUES (p_id, p_trip_id, p_employee_id, p_amount, p_date, 'pending', p_user_id);
END$$

CREATE PROCEDURE sp_advance_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM advances WHERE id = p_id;
END$$

DELIMITER ;
