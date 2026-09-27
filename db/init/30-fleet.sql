-- ================================================================ routines
-- Vehicles, fuel loads, maintenance and the fuel validation rule.

USE sysportdb;

DELIMITER $$

-- BR-18: fuel amount consistency, odometer monotonicity and trip/vehicle match.
CREATE PROCEDURE sp_validate_fuel_load(IN p_vehicle_id BIGINT, IN p_trip_id BIGINT,
    IN p_load_date DATETIME, IN p_liters DECIMAL(8,2), IN p_price DECIMAL(8,3),
    IN p_amount DECIMAL(12,2), IN p_odometer DECIMAL(10,1), OUT p_problems TEXT)
p: BEGIN
  DECLARE v_mileage DECIMAL(10,1) DEFAULT NULL;
  DECLARE v_trip_vehicle BIGINT DEFAULT NULL;

  SET p_problems = NULL;
  IF p_vehicle_id IS NULL OR p_vehicle_id = 0 THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Debe seleccionar una unidad');
  END IF;
  IF p_load_date IS NULL THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de carga es obligatoria');
  ELSEIF NOT fn_date_valid(DATE(p_load_date)) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'La fecha de carga no es valida');
  END IF;
  IF NOT fn_liters_valid(p_liters) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'Los litros son invalidos o exceden el maximo permitido');
  END IF;
  IF NOT fn_price_per_liter_valid(p_price) THEN
    SET p_problems = CONCAT_WS('; ', p_problems, 'El precio por litro es invalido o excede el maximo permitido');
  END IF;
  IF p_amount IS NULL OR p_amount <= 0 OR NOT fn_money_valid(p_amount) THEN
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

-- ---------------------------------------------------------------- vehicles

CREATE PROCEDURE sp_vehicles_search(IN p_term VARCHAR(150))
p: BEGIN
  SELECT * FROM v_vehicle
  WHERE p_term IS NULL OR p_term = ''
     OR internal_code LIKE CONCAT('%', p_term, '%') OR plates LIKE CONCAT('%', p_term, '%')
     OR brand LIKE CONCAT('%', p_term, '%') OR model LIKE CONCAT('%', p_term, '%')
  ORDER BY internal_code;
END$$

CREATE PROCEDURE sp_vehicle_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT * FROM v_vehicle WHERE id = p_id;
END$$

-- Status is not editable here: new vehicles start 'available' and later changes
-- go through sp_vehicle_set_status or the trip lifecycle.
CREATE PROCEDURE sp_vehicle_save(IN p_id BIGINT, IN p_code VARCHAR(30), IN p_plates VARCHAR(20),
    IN p_brand VARCHAR(50), IN p_model VARCHAR(50), IN p_year INT, IN p_serial VARCHAR(60),
    IN p_type VARCHAR(50), IN p_capacity DECIMAL(10,1), IN p_mileage DECIMAL(10,1),
    OUT p_new_id BIGINT, OUT p_problems TEXT)
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
            p_capacity, COALESCE(p_mileage, 0), 'available');
  ELSE
    SET p_new_id = p_id;
    -- BR-21: an edit never lowers the odometer.
    UPDATE vehicles SET internal_code = p_code, plates = p_plates, brand = p_brand,
                        model = p_model, year = p_year, serial_number = p_serial,
                        vehicle_type = p_type, load_capacity = p_capacity,
                        mileage = GREATEST(mileage, COALESCE(p_mileage, mileage))
    WHERE id = p_id;
  END IF;
END$$

-- Manual conditions only: 'assigned'/'on_trip' are owned by the trip lifecycle
-- (assignment/departure/arrival), and a vehicle with an active trip is locked.
CREATE PROCEDURE sp_vehicle_set_status(IN p_id BIGINT, IN p_status VARCHAR(20), OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF p_status NOT IN ('available','maintenance','out_of_service','decommissioned') THEN
    SET p_problems = 'Estado de unidad no valido para cambio manual'; LEAVE p;
  END IF;
  IF (SELECT COUNT(*) FROM trips WHERE vehicle_id = p_id AND status IN ('scheduled','in_transit')) > 0 THEN
    SET p_problems = 'La unidad tiene un viaje activo; no se puede cambiar el estado'; LEAVE p;
  END IF;
  UPDATE vehicles SET status = p_status WHERE id = p_id;
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Unidad no encontrada';
  END IF;
END$$

CREATE PROCEDURE sp_vehicle_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  SET p_problems = NULL;
  IF (SELECT COUNT(*) FROM trips WHERE vehicle_id = p_id) > 0
     OR (SELECT COUNT(*) FROM fuel_loads WHERE vehicle_id = p_id) > 0
     OR (SELECT COUNT(*) FROM maintenance WHERE vehicle_id = p_id) > 0 THEN
    SET p_problems = 'No se puede eliminar: la unidad tiene viajes, cargas o mantenimientos';
    LEAVE p;
  END IF;
  DELETE FROM vehicles WHERE id = p_id;
  IF ROW_COUNT() = 0 THEN
    SET p_problems = 'Unidad no encontrada';
  END IF;
END$$

CREATE PROCEDURE sp_eligible_vehicles_full(IN p_start DATETIME, IN p_end DATETIME)
p: BEGIN
  SELECT * FROM v_vehicle v
  WHERE v.status = 'available'
    AND NOT EXISTS (
      SELECT 1 FROM trips t
      WHERE t.vehicle_id = v.id
        AND t.status IN ('scheduled','in_transit')
        AND t.planned_start < p_end AND t.planned_end > p_start)
  ORDER BY v.internal_code;
END$$

-- ---------------------------------------------------------------- fuel

CREATE PROCEDURE sp_fuel_by_vehicle(IN p_vehicle_id BIGINT)
p: BEGIN
  SELECT * FROM v_fuel_load
  WHERE vehicle_id = p_vehicle_id
  ORDER BY load_date DESC;
END$$

CREATE PROCEDURE sp_fuel_by_trip(IN p_trip_id BIGINT)
p: BEGIN
  SELECT * FROM v_fuel_load
  WHERE trip_id = p_trip_id
  ORDER BY load_date;
END$$

CREATE PROCEDURE sp_fuel_list()
p: BEGIN
  SELECT * FROM v_fuel_load ORDER BY load_date DESC;
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
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al registrar la carga de combustible';
  END;
  CALL sp_validate_fuel_load(p_vehicle_id, p_trip_id, p_load_date, p_liters, p_price, p_amount, p_odometer, p_problems);
  IF p_problems IS NOT NULL THEN LEAVE p; END IF;
  START TRANSACTION;
  CALL sp_next_id('fuel_loads', p_id);
  INSERT INTO fuel_loads (id, vehicle_id, trip_id, fuel_station, load_date, liters,
                          price_per_liter, amount, odometer_reading, created_by)
  VALUES (p_id, p_vehicle_id, p_trip_id, p_station, p_load_date, p_liters, p_price,
          p_amount, p_odometer, p_user_id);
  IF p_odometer IS NOT NULL THEN
    UPDATE vehicles SET mileage = p_odometer WHERE id = p_vehicle_id AND mileage < p_odometer;
  END IF;
  COMMIT;
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
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'Error inesperado al registrar el mantenimiento';
  END;
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

  START TRANSACTION;
  CALL sp_next_id('maintenance', p_id);
  INSERT INTO maintenance (id, vehicle_id, maintenance_date, odometer_reading, maintenance_type,
                           work_performed, provider, cost, next_service_date, next_service_km, created_by)
  VALUES (p_id, p_vehicle_id, p_date, p_odometer, COALESCE(p_type, 'preventive'), p_work,
          p_provider, COALESCE(p_cost, 0), p_next_date, p_next_km, p_user_id);
  IF p_odometer IS NOT NULL THEN
    UPDATE vehicles SET mileage = p_odometer WHERE id = p_vehicle_id AND mileage < p_odometer;
  END IF;
  COMMIT;
END$$

CREATE PROCEDURE sp_maintenance_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM maintenance WHERE id = p_id;
END$$

DELIMITER ;
