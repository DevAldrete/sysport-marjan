-- ================================================================ routines
-- Clients, negotiated rates and routes (master data).

USE sysportdb;

DELIMITER $$

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
  IF (SELECT COUNT(*) FROM service_requests WHERE client_id = p_id) > 0
     OR (SELECT COUNT(*) FROM client_rates WHERE client_id = p_id) > 0
     OR (SELECT COUNT(*) FROM invoices WHERE client_id = p_id) > 0 THEN
    SET p_problems = 'No se puede eliminar: el cliente tiene solicitudes, tarifas o facturas';
    LEAVE p;
  END IF;
  DELETE FROM clients WHERE id = p_id;
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Cliente no encontrado';
  END IF;
END$$

-- Only the manual lifecycle states can be set by hand; 'active'/'inactive'.
CREATE PROCEDURE sp_client_set_status(IN p_id BIGINT, IN p_status VARCHAR(20), OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_status NOT IN ('active','inactive') THEN
    SET p_problems = 'Estado de cliente no valido'; LEAVE p;
  END IF;
  UPDATE clients SET status = p_status WHERE id = p_id;
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Cliente no encontrado';
  END IF;
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
  -- BR-04: one unambiguous rate per client/route at any point in time.
  IF p_client_id IS NOT NULL AND p_client_id <> 0 AND p_route_id IS NOT NULL
     AND p_route_id <> 0 AND p_valid_from IS NOT NULL
     AND (SELECT COUNT(*) FROM client_rates
          WHERE client_id = p_client_id AND route_id = p_route_id
            AND (p_id IS NULL OR p_id = 0 OR id <> p_id)
            AND valid_from <= COALESCE(p_valid_to, '9999-12-31')
            AND COALESCE(valid_to, '9999-12-31') >= p_valid_from) > 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems,
        'Ya existe una tarifa vigente para esa ruta en ese periodo');
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
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Tarifa no encontrada';
  END IF;
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
  IF (SELECT COUNT(*) FROM service_requests WHERE route_id = p_id) > 0
     OR (SELECT COUNT(*) FROM client_rates WHERE route_id = p_id) > 0 THEN
    SET p_problems = 'No se puede eliminar: la ruta tiene solicitudes o tarifas';
    LEAVE p;
  END IF;
  DELETE FROM routes WHERE id = p_id;
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Ruta no encontrada';
  END IF;
END$$

-- ================================================================ extras


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

DELIMITER ;
