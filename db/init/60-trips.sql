-- ================================================================ routines
-- Trip assignment, execution, deliveries and incidents (BR-05..BR-12, BR-15, BR-21).

USE sysportdb;

DELIMITER $$

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
    FROM service_requests WHERE id = p_request_id FOR UPDATE;
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
  DECLARE v_req_status VARCHAR(20) DEFAULT NULL;
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
  SELECT status INTO v_req_status FROM service_requests WHERE id = v_request FOR UPDATE;
  IF NOT fn_request_can_transition(v_req_status, 'in_transit') THEN
    ROLLBACK; SET p_problems = CONCAT('La solicitud no puede iniciar transito desde ', COALESCE(v_req_status, 'desconocido')); LEAVE p;
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
  DECLARE v_request BIGINT;
  DECLARE v_requires TINYINT DEFAULT 1;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al registrar la llegada';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status, vehicle_id, employee_id, service_request_id
    INTO v_status, v_vehicle, v_employee, v_request
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
  -- BR-21: the trip's actual km is a distance, so add it to the odometer.
  UPDATE vehicles SET status = 'available',
                      mileage = mileage + COALESCE(p_actual_km, 0)
    WHERE id = v_vehicle;
  UPDATE employees SET status = 'available' WHERE id = v_employee;
  -- A request that does not require documents has nothing left to prove, so
  -- arrival delivers it (otherwise it could never reach 'delivered'/close).
  SELECT requires_documents INTO v_requires FROM service_requests WHERE id = v_request;
  IF NOT v_requires THEN
    UPDATE service_requests SET status = 'delivered', updated_by = p_user_id
      WHERE id = v_request AND status = 'in_transit';
  END IF;
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

-- BR-15: reassign before departure, validated like a new assignment and audited.
-- The validators intentionally ignore the request's own trip, so they can be
-- reused here without flagging the current trip as a conflict.
CREATE PROCEDURE sp_reassign_trip(IN p_trip_id BIGINT, IN p_vehicle_id BIGINT,
    IN p_operator_id BIGINT, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_request BIGINT;
  DECLARE v_req_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_audit_id BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al reasignar el viaje';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status, service_request_id INTO v_status, v_request
    FROM trips WHERE id = p_trip_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Viaje no encontrado'; LEAVE p;
  END IF;
  IF v_status <> 'scheduled' THEN
    ROLLBACK; SET p_problems = 'Solo se puede reasignar un viaje programado (aun no inicia)'; LEAVE p;
  END IF;
  SELECT status INTO v_req_status FROM service_requests WHERE id = v_request FOR UPDATE;
  IF v_req_status <> 'scheduled' THEN
    ROLLBACK; SET p_problems = 'La solicitud debe estar programada para reasignar el viaje'; LEAVE p;
  END IF;

  -- Lock the candidate resources before checking overlaps, like a new assignment.
  SELECT id INTO @lock_v FROM vehicles WHERE id = p_vehicle_id FOR UPDATE;
  SELECT id INTO @lock_e FROM employees WHERE id = p_operator_id FOR UPDATE;
  CALL validate_vehicle_assignment_into(v_request, p_vehicle_id, p_problems);
  CALL validate_operator_assignment_into(v_request, p_operator_id, p_problems);
  IF p_problems IS NOT NULL THEN
    ROLLBACK; LEAVE p;
  END IF;

  UPDATE trips SET vehicle_id = p_vehicle_id, employee_id = p_operator_id, updated_by = p_user_id
  WHERE id = p_trip_id;
  CALL sp_next_id('audit_log', v_audit_id);
  INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
  VALUES (v_audit_id, p_user_id, 'trip', p_trip_id, 'reassigned',
          CONCAT('vehicle=', p_vehicle_id, ', operator=', p_operator_id));
  COMMIT;
END$$

-- BR-14: cleanup delete, only while the trip has not started. Audit rows are
-- kept (BR-22). Covers its own children in one transaction.
CREATE PROCEDURE sp_trip_delete(IN p_trip_id BIGINT, IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_status VARCHAR(20) DEFAULT NULL;
  DECLARE v_request BIGINT;
  DECLARE v_req_status VARCHAR(20) DEFAULT NULL;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'No se puede eliminar el viaje: tiene registros relacionados';
  END;

  SET p_problems = NULL;
  START TRANSACTION;
  SELECT status, service_request_id INTO v_status, v_request
    FROM trips WHERE id = p_trip_id FOR UPDATE;
  IF v_status IS NULL THEN
    ROLLBACK; SET p_problems = 'Viaje no encontrado'; LEAVE p;
  END IF;
  IF v_status NOT IN ('scheduled','cancelled') THEN
    ROLLBACK; SET p_problems = 'Solo se puede eliminar un viaje programado o cancelado'; LEAVE p;
  END IF;

  DELETE FROM expenses WHERE trip_id = p_trip_id;
  DELETE FROM advances WHERE trip_id = p_trip_id;
  DELETE FROM incidents WHERE trip_id = p_trip_id;
  DELETE FROM deliveries WHERE trip_id = p_trip_id;
  UPDATE fuel_loads SET trip_id = NULL WHERE trip_id = p_trip_id;
  DELETE FROM trips WHERE id = p_trip_id;

  -- Unassign the request only if the trip never progressed past assigned.
  SELECT status INTO v_req_status FROM service_requests WHERE id = v_request FOR UPDATE;
  IF v_req_status = 'assigned' THEN
    UPDATE service_requests SET status = 'scheduled', updated_by = p_user_id WHERE id = v_request;
  END IF;
  COMMIT;
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
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_changes = -1;
  END;

  SET v_now = NOW();
  START TRANSACTION;
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
  COMMIT;

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
  DECLARE v_trip_status VARCHAR(20);
  DECLARE v_existing BIGINT;
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al registrar la entrega';
  END;
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
  IF p_status IS NOT NULL AND p_status NOT IN ('pending_documents','complete') THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Estado de entrega no valido');
  END IF;
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;

  START TRANSACTION;
  SELECT t.status, sr.id, sr.status INTO v_trip_status, v_request, v_req_status
    FROM trips t JOIN service_requests sr ON sr.id = t.service_request_id
    WHERE t.id = p_trip_id FOR UPDATE;
  IF v_request IS NULL THEN
    ROLLBACK; SET p_problems = 'Viaje no encontrado'; LEAVE p;
  END IF;
  -- BR-13: the trip must be finished before its delivery is recorded.
  IF v_trip_status <> 'completed' THEN
    ROLLBACK; SET p_problems = 'Debe registrar la llegada del viaje antes de la entrega'; LEAVE p;
  END IF;

  SELECT id INTO v_existing FROM deliveries WHERE trip_id = p_trip_id;
  IF v_existing IS NULL THEN
    CALL sp_next_id('deliveries', p_id);
    INSERT INTO deliveries (id, trip_id, actual_datetime, received_by, evidence_reference, status, created_by)
    VALUES (p_id, p_trip_id, p_actual, p_received_by, p_evidence,
            COALESCE(p_status, 'pending_documents'), p_user_id);
  ELSE
    SET p_id = v_existing;
    UPDATE deliveries SET actual_datetime = p_actual, received_by = p_received_by,
                          evidence_reference = p_evidence, status = COALESCE(p_status, status)
    WHERE trip_id = p_trip_id;
  END IF;

  IF v_req_status = 'in_transit' THEN
    UPDATE service_requests SET status = 'delivered', updated_by = p_user_id WHERE id = v_request;
  END IF;
  COMMIT;
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
  IF p_type IS NULL OR p_type NOT IN ('accident','mechanical_failure','delay',
      'road_closure','cargo_damage','documentation_issue','other') THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El tipo de incidencia no es valido');
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

DELIMITER ;
