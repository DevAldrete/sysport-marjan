-- ================================================================ routines
-- Service requests and their lifecycle actions (BR-03/04, BR-13).

USE sysportdb;

DELIMITER $$

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
          AND service_request_id <> p_request_id
          AND status IN ('scheduled','in_transit')
          AND planned_start < v_end AND planned_end > v_start;
      IF v_conflicts > 0 THEN
        SET p_problems = CONCAT_WS('; ', p_problems,
            'La unidad ya tiene un viaje en ese periodo');
      END IF;
    END IF;
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

-- ---------------------------------------------------------------- requests

CREATE PROCEDURE sp_requests_search(IN p_folio VARCHAR(20), IN p_client_id BIGINT,
    IN p_status VARCHAR(20), IN p_from DATETIME, IN p_to DATETIME)
p: BEGIN
  SELECT * FROM v_service_request
  WHERE (p_folio IS NULL OR p_folio = '' OR folio LIKE CONCAT('%', p_folio, '%'))
    AND (p_client_id IS NULL OR client_id = p_client_id)
    AND (p_status IS NULL OR status = p_status)
    -- A date range filters scheduled work without hiding requests that still
    -- have no scheduled dates (NULL never matches a comparison).
    AND (p_from IS NULL OR pickup_date_scheduled IS NULL OR pickup_date_scheduled >= p_from)
    AND (p_to IS NULL OR pickup_date_scheduled IS NULL OR pickup_date_scheduled <= p_to)
  ORDER BY created_at DESC;
END$$

CREATE PROCEDURE sp_request_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT * FROM v_service_request WHERE id = p_id;
END$$

CREATE PROCEDURE sp_requests_by_status(IN p_status VARCHAR(20))
p: BEGIN
  SELECT * FROM v_service_request
  WHERE status = p_status
  ORDER BY pickup_date_scheduled;
END$$

CREATE PROCEDURE sp_requests_pending_billing()
p: BEGIN
  SELECT * FROM v_service_request
  WHERE agreed_rate IS NOT NULL AND agreed_rate > 0
    AND status <> 'cancelled'
    AND NOT EXISTS (SELECT 1 FROM invoices i WHERE i.service_request_id = v_service_request.id)
  ORDER BY created_at DESC;
END$$

CREATE PROCEDURE sp_request_create(IN p_client_id BIGINT, IN p_route_id BIGINT,
    IN p_cargo VARCHAR(255), IN p_weight DECIMAL(10,1), IN p_pickup DATETIME,
    IN p_delivery DATETIME, IN p_rate DECIMAL(12,2), IN p_requires_documents BOOLEAN,
    IN p_notes VARCHAR(500), IN p_user_id BIGINT, OUT p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_year INT;
  DECLARE v_folio VARCHAR(20);
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al crear la solicitud';
  END;
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

  START TRANSACTION;
  -- Allocate the id first: holding the service_requests sequence row until
  -- commit serialises folio generation, so fn_next_folio's MAX()+1 is safe.
  CALL sp_next_id('service_requests', p_id);
  SET v_year = YEAR(COALESCE(p_pickup, CURDATE()));
  SET v_folio = fn_next_folio(v_year);
  INSERT INTO service_requests
    (id, folio, client_id, route_id, cargo_description, estimated_weight,
     pickup_date_scheduled, delivery_date_scheduled, agreed_rate,
     requires_documents, status, notes, created_by, updated_by)
  VALUES (p_id, v_folio, p_client_id, p_route_id, p_cargo, p_weight, p_pickup, p_delivery,
          p_rate, COALESCE(p_requires_documents, TRUE), 'requested', p_notes, p_user_id, p_user_id);
  COMMIT;
END$$

-- Edits the request data only. Status is owned by the action procedures
-- (authorize/schedule/cancel/...) and is never written from here.
CREATE PROCEDURE sp_request_update(IN p_id BIGINT, IN p_client_id BIGINT, IN p_route_id BIGINT,
    IN p_cargo VARCHAR(255), IN p_weight DECIMAL(10,1), IN p_pickup DATETIME,
    IN p_delivery DATETIME, IN p_rate DECIMAL(12,2), IN p_requires_documents BOOLEAN,
    IN p_notes VARCHAR(500), IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF NOT fn_measure_valid(p_weight) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El peso estimado es invalido o excede el maximo permitido');
  END IF;
  IF p_rate IS NOT NULL AND (p_rate <= 0 OR NOT fn_money_valid(p_rate)) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La tarifa acordada debe ser mayor a cero y dentro del rango permitido');
  END IF;
  IF (p_pickup IS NULL) <> (p_delivery IS NULL) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe indicar ambas fechas o ninguna');
  ELSEIF p_pickup IS NOT NULL AND p_delivery <= p_pickup THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de entrega debe ser posterior a la de recoleccion');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  UPDATE service_requests SET client_id = p_client_id, route_id = p_route_id,
                              cargo_description = p_cargo, estimated_weight = p_weight,
                              pickup_date_scheduled = p_pickup, delivery_date_scheduled = p_delivery,
                              agreed_rate = p_rate, requires_documents = p_requires_documents,
                              notes = p_notes, updated_by = p_user_id
  WHERE id = p_id;
END$$

-- BR-14: careful cascade in one transaction. Audit rows are intentionally kept
-- (BR-22), even for a hard delete.
CREATE PROCEDURE sp_request_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'No se puede eliminar la solicitud: tiene registros relacionados';
  END;
  SET p_problems = NULL;
  IF (SELECT COUNT(*) FROM service_requests WHERE id = p_id) = 0 THEN
    SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;
  START TRANSACTION;
  DELETE FROM payments WHERE invoice_id IN (SELECT id FROM invoices WHERE service_request_id = p_id);
  DELETE FROM invoices WHERE service_request_id = p_id;
  DELETE FROM expenses WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM advances WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM incidents WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM deliveries WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  UPDATE fuel_loads SET trip_id = NULL WHERE trip_id IN (SELECT id FROM trips WHERE service_request_id = p_id);
  DELETE FROM trips WHERE service_request_id = p_id;
  DELETE FROM service_requests WHERE id = p_id;
  COMMIT;
END$$

DELIMITER ;
