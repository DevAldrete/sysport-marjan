-- ================================================================ routines
-- Employees/operators, licences and the operator assignment rule.

USE sysportdb;

DELIMITER $$

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
          AND service_request_id <> p_request_id
          AND status IN ('scheduled','in_transit')
          AND planned_start < v_end AND planned_end > v_start;
      IF v_conflicts > 0 THEN
        SET p_problems = CONCAT_WS('; ', p_problems,
            'El operador ya tiene un viaje en ese periodo');
      END IF;
    END IF;
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

-- Status is not editable here: new employees start 'available' and later changes
-- go through sp_employee_set_status or the trip lifecycle.
CREATE PROCEDURE sp_employee_save(IN p_id BIGINT, IN p_name VARCHAR(150), IN p_address VARCHAR(255),
    IN p_phone VARCHAR(30), IN p_email VARCHAR(150), IN p_rfc VARCHAR(13), IN p_curp VARCHAR(18),
    IN p_ec_name VARCHAR(150), IN p_ec_phone VARCHAR(30), IN p_license_id BIGINT,
    IN p_license_number VARCHAR(50), IN p_license_type VARCHAR(50), IN p_license_issue DATE,
    IN p_license_expiry DATE,
    OUT p_new_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_license_id BIGINT DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al guardar el operador';
  END;
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

  -- License and employee are written together so a failure cannot leave an
  -- orphan license (or an employee pointing at a half-written one).
  START TRANSACTION;
  -- Start from the currently linked license, so editing other fields with a
  -- blank license number keeps it instead of silently clearing license_id.
  IF p_id IS NOT NULL AND p_id <> 0 THEN
    SELECT license_id INTO v_license_id FROM employees WHERE id = p_id;
  END IF;
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
  ELSEIF p_license_id IS NOT NULL AND p_license_id <> 0 THEN
    -- Blank number means "leave the license as it is", not "clear it".
    SET v_license_id = p_license_id;
  END IF;

  IF p_id IS NULL OR p_id = 0 THEN
    CALL sp_next_id('employees', p_new_id);
    INSERT INTO employees (id, name, address, phone, email, rfc, curp,
                           emergency_contact_name, emergency_contact_phone, license_id, status)
    VALUES (p_new_id, p_name, p_address, p_phone, p_email, p_rfc, p_curp,
            p_ec_name, p_ec_phone, v_license_id, 'available');
  ELSE
    SET p_new_id = p_id;
    UPDATE employees SET name = p_name, address = p_address, phone = p_phone, email = p_email,
                         rfc = p_rfc, curp = p_curp, emergency_contact_name = p_ec_name,
                         emergency_contact_phone = p_ec_phone, license_id = v_license_id
    WHERE id = p_id;
  END IF;
  COMMIT;
END$$

CREATE PROCEDURE sp_employee_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_license BIGINT DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'No se puede eliminar el operador: tiene registros relacionados';
  END;
  SET p_problems = NULL;
  IF (SELECT COUNT(*) FROM trips WHERE employee_id = p_id) > 0
     OR (SELECT COUNT(*) FROM advances WHERE employee_id = p_id) > 0
     OR (SELECT COUNT(*) FROM users WHERE employee_id = p_id) > 0 THEN
    SET p_problems = 'No se puede eliminar: el operador tiene viajes, anticipos o un usuario asociado';
    LEAVE p;
  END IF;
  SELECT license_id INTO v_license FROM employees WHERE id = p_id;
  IF v_license IS NULL AND (SELECT COUNT(*) FROM employees WHERE id = p_id) = 0 THEN
    SET p_problems = 'Operador no encontrado';
    LEAVE p;
  END IF;
  START TRANSACTION;
  DELETE FROM employees WHERE id = p_id;
  IF v_license IS NOT NULL THEN
    DELETE FROM licenses WHERE id = v_license;
  END IF;
  COMMIT;
END$$

-- Manual conditions only: 'on_trip' is owned by the trip lifecycle, and an
-- employee with an active trip cannot be moved by hand.
CREATE PROCEDURE sp_employee_set_status(IN p_id BIGINT, IN p_status VARCHAR(20), OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_status NOT IN ('available','resting','vacation','incapacitated','terminated') THEN
    SET p_problems = 'Estado de operador no valido para cambio manual'; LEAVE p;
  END IF;
  IF (SELECT COUNT(*) FROM trips WHERE employee_id = p_id AND status IN ('scheduled','in_transit')) > 0 THEN
    SET p_problems = 'El operador tiene un viaje activo; no se puede cambiar el estado'; LEAVE p;
  END IF;
  UPDATE employees SET status = p_status WHERE id = p_id;
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Operador no encontrado';
  END IF;
END$$

DELIMITER ;
