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
  SELECT status, pickup_date_scheduled, delivery_date_scheduled
    INTO v_req_status, v_start, v_end
    FROM service_requests WHERE id = p_request_id;
  IF v_req_status IS NULL THEN
    SET p_problems = 'Solicitud no encontrada';
    LEAVE p;
  END IF;
  SET v_weight = fn_request_weight(p_request_id);
  -- 'assigned' with a still-scheduled trip is the reassignment case: the
  -- request is ready to be pointed at a different unit/operator.
  IF v_req_status <> 'scheduled'
     AND NOT (v_req_status = 'assigned'
              AND EXISTS (SELECT 1 FROM trips t
                          WHERE t.service_request_id = p_request_id
                            AND t.status = 'scheduled')) THEN
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
-- BR-03: schedule an authorized request, or correct the dates of one that has
-- not started (scheduled/assigned). Only 'authorized' advances the lifecycle;
-- rescheduling keeps the status so the assignment and sweep keep working.
CREATE PROCEDURE sp_schedule_request(IN p_request_id BIGINT, IN p_pickup DATETIME,
    IN p_delivery DATETIME, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_trip_status VARCHAR(20) DEFAULT NULL;
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
  IF NOT fn_request_reschedulable(v_status) THEN
    ROLLBACK; SET p_problems = CONCAT('No se puede programar una solicitud en estado ', v_status); LEAVE p;
  END IF;
  IF p_pickup IS NULL OR p_delivery IS NULL THEN
    ROLLBACK; SET p_problems = 'Las fechas programadas de recoleccion y entrega son obligatorias'; LEAVE p;
  END IF;
  IF p_delivery <= p_pickup THEN
    ROLLBACK; SET p_problems = 'La fecha de entrega debe ser posterior a la de recoleccion'; LEAVE p;
  END IF;
  -- Rescheduling an assigned request must not touch a trip already on the road.
  IF v_status = 'assigned' THEN
    SELECT status INTO v_trip_status FROM trips
      WHERE service_request_id = p_request_id FOR UPDATE;
    IF v_trip_status IS NULL OR v_trip_status <> 'scheduled' THEN
      ROLLBACK; SET p_problems = 'No se puede reprogramar: el viaje ya inicio'; LEAVE p;
    END IF;
  END IF;
  UPDATE service_requests
    SET pickup_date_scheduled = p_pickup, delivery_date_scheduled = p_delivery,
        status = IF(v_status = 'authorized', 'scheduled', v_status), updated_by = p_user_id
    WHERE id = p_request_id;
  IF v_status = 'assigned' THEN
    UPDATE trips SET planned_start = p_pickup, planned_end = p_delivery, updated_by = p_user_id
      WHERE service_request_id = p_request_id;
  END IF;
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

-- FR-INV-1: only delivered/closed requests can be invoiced (matches
-- sp_create_invoice_from_request), so this list never offers unbillable rows.
CREATE PROCEDURE sp_requests_pending_billing()
p: BEGIN
  SELECT * FROM v_service_request
  WHERE agreed_rate IS NOT NULL AND agreed_rate > 0
    AND status IN ('delivered','closed')
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

-- ---------------------------------------------------------------- packages

CREATE PROCEDURE sp_request_packages(IN p_request_id BIGINT)
p: BEGIN
  SELECT id, service_request_id, line_no, description, quantity, unit, unit_weight,
         received_quantity, receipt_condition
  FROM request_packages WHERE service_request_id = p_request_id
  ORDER BY line_no;
END$$

-- Packages are written row by row by the repository inside one Java
-- transaction (see PackageRepository), so this procedure validates and writes
-- but deliberately does not start, commit or roll back a transaction.
CREATE PROCEDURE sp_package_save(IN p_id BIGINT, IN p_request_id BIGINT, IN p_line_no INT,
    IN p_description VARCHAR(255), IN p_quantity DECIMAL(10,2), IN p_unit VARCHAR(20),
    IN p_unit_weight DECIMAL(10,1), IN p_user_id BIGINT,
    OUT p_new_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  SET p_new_id = NULL;
  IF p_request_id IS NULL OR p_request_id = 0
     OR (SELECT COUNT(*) FROM service_requests WHERE id = p_request_id) = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La solicitud del paquete no existe');
  END IF;
  IF p_description IS NULL OR p_description = '' THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La descripcion del paquete es obligatoria');
  END IF;
  IF p_quantity IS NULL OR p_quantity <= 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La cantidad del paquete debe ser mayor a cero');
  END IF;
  IF p_unit IS NULL OR p_unit NOT IN ('caja','paleta','saco','bulto','pieza','contenedor','otro') THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La unidad del paquete no es valida');
  END IF;
  IF p_unit_weight IS NOT NULL AND NOT fn_measure_valid(p_unit_weight) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El peso por unidad no es valido');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  IF p_id IS NULL OR p_id = 0 THEN
    CALL sp_next_id('request_packages', p_new_id);
    INSERT INTO request_packages
      (id, service_request_id, line_no, description, quantity, unit, unit_weight)
    VALUES (p_new_id, p_request_id, p_line_no, p_description, p_quantity, p_unit, p_unit_weight);
  ELSE
    SET p_new_id = p_id;
    UPDATE request_packages
      SET line_no = p_line_no, description = p_description, quantity = p_quantity,
          unit = p_unit, unit_weight = p_unit_weight
    WHERE id = p_id AND service_request_id = p_request_id;
    IF (SELECT COUNT(*) FROM request_packages WHERE id = p_id) = 0 THEN
      SET p_problems = 'Paquete no encontrado';
      SET p_new_id = NULL;
    END IF;
  END IF;
END$$

-- BR-26: a package line can be edited until the request is in transit; after
-- that the cargo is history (receipts are recorded, not rewritten).
CREATE PROCEDURE sp_package_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  SET p_problems = NULL;
  SELECT sr.status INTO v_status
  FROM request_packages pkg
  JOIN service_requests sr ON sr.id = pkg.service_request_id
  WHERE pkg.id = p_id;
  IF v_status IS NULL THEN
    SET p_problems = 'Paquete no encontrado'; LEAVE p;
  END IF;
  IF v_status IN ('in_transit','delivered','closed') THEN
    SET p_problems = 'No se puede modificar la carga de una solicitud en transito o entregada';
    LEAVE p;
  END IF;
  DELETE FROM request_packages WHERE id = p_id;
END$$

-- Per-unit tracking: how much of a package arrived and its condition.
CREATE PROCEDURE sp_package_receipt_save(IN p_id BIGINT, IN p_received DECIMAL(10,2),
    IN p_condition VARCHAR(20), OUT p_problems TEXT)
p: BEGIN
  DECLARE v_quantity DECIMAL(10,2) DEFAULT NULL;
  SET p_problems = NULL;
  SELECT quantity INTO v_quantity FROM request_packages WHERE id = p_id;
  IF v_quantity IS NULL THEN
    SET p_problems = 'Paquete no encontrado'; LEAVE p;
  END IF;
  IF p_received IS NULL OR p_received < 0 OR p_received > v_quantity THEN
    SET p_problems = CONCAT('La cantidad recibida debe estar entre 0 y ', v_quantity); LEAVE p;
  END IF;
  IF p_condition IS NULL OR p_condition NOT IN ('ok','shortage','damaged','missing') THEN
    SET p_problems = 'La condicion del paquete no es valida'; LEAVE p;
  END IF;
  UPDATE request_packages SET received_quantity = p_received, receipt_condition = p_condition
  WHERE id = p_id;
END$$

-- BR-14: careful cascade in one transaction. Audit rows are intentionally kept
-- (BR-22), even for a hard delete. BR-26: a request with a trip or an invoice
-- is history, so it must be cancelled instead of deleted.
CREATE PROCEDURE sp_request_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'No se puede eliminar la solicitud: tiene registros relacionados';
  END;
  SET p_problems = NULL;
  SELECT status INTO v_status FROM service_requests WHERE id = p_id;
  IF v_status IS NULL THEN
    SET p_problems = 'Solicitud no encontrada'; LEAVE p;
  END IF;
  IF v_status IN ('assigned','in_transit','delivered','closed') THEN
    SET p_problems = 'Solo se puede eliminar una solicitud sin viaje asignado; cancelela en su lugar';
    LEAVE p;
  END IF;
  IF (SELECT COUNT(*) FROM invoices WHERE service_request_id = p_id) > 0 THEN
    SET p_problems = 'No se puede eliminar una solicitud con factura; cancelela en su lugar';
    LEAVE p;
  END IF;
  START TRANSACTION;
  DELETE FROM request_packages WHERE service_request_id = p_id;
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
