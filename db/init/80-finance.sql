-- ================================================================ routines
-- Invoices and payments (BR-19/20).

USE sysportdb;

DELIMITER $$

-- BR-19: register a payment and re-derive the invoice status, in one transaction.
CREATE PROCEDURE sp_register_payment(IN p_invoice_id BIGINT, IN p_amount DECIMAL(12,2),
    IN p_date DATE, IN p_method VARCHAR(30), IN p_user_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_amount DECIMAL(12,2) DEFAULT NULL;
  DECLARE v_paid DECIMAL(12,2) DEFAULT 0;
  DECLARE v_due DATE;
  DECLARE v_status VARCHAR(20);
  DECLARE v_date DATE;
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
  IF v_status = 'cancelled' THEN
    ROLLBACK; SET p_problems = 'No se pueden registrar pagos de una factura cancelada'; LEAVE p;
  END IF;
  SELECT COALESCE(SUM(amount),0) INTO v_paid FROM payments WHERE invoice_id = p_invoice_id;
  IF p_amount IS NULL OR p_amount <= 0 THEN
    ROLLBACK; SET p_problems = 'El pago debe ser mayor a cero y dentro del rango permitido'; LEAVE p;
  END IF;
  IF v_paid + p_amount > v_amount THEN
    ROLLBACK; SET p_problems = CONCAT('El pago excede el saldo pendiente (', v_amount - v_paid, ')'); LEAVE p;
  END IF;
  SET v_date = COALESCE(p_date, CURDATE());
  CALL sp_next_id('payments', v_payment_id);
  INSERT INTO payments (id, invoice_id, amount, payment_date, payment_method, created_by)
    VALUES (v_payment_id, p_invoice_id, p_amount, v_date, COALESCE(p_method, 'cash'), p_user_id);
  UPDATE invoices SET status = fn_invoice_status(v_status, v_amount, v_paid + p_amount, v_due, v_date)
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
  DECLARE v_number VARCHAR(30);
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
  -- Allocate the id first (locks the invoices sequence row through commit) so
  -- the MAX()+1 invoice number cannot race a concurrent creation.
  CALL sp_next_id('invoices', p_invoice_id);
  SET v_number = fn_next_invoice_number(YEAR(v_issue));
  INSERT INTO invoices (id, client_id, service_request_id, invoice_number, amount,
                        issue_date, due_date, status, created_by)
    VALUES (p_invoice_id, v_client, p_request_id, v_number,
            v_amount, v_issue, v_due, 'pending', p_user_id);
  COMMIT;
END$$

-- ---------------------------------------------------------------- invoices

CREATE PROCEDURE sp_invoices_search(IN p_status VARCHAR(20), IN p_client_id BIGINT)
p: BEGIN
  SELECT * FROM v_invoice
  WHERE (p_status IS NULL OR status = p_status)
    AND (p_client_id IS NULL OR client_id = p_client_id)
  ORDER BY issue_date DESC;
END$$

CREATE PROCEDURE sp_invoice_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT * FROM v_invoice WHERE id = p_id;
END$$

CREATE PROCEDURE sp_invoice_by_request(IN p_request_id BIGINT)
p: BEGIN
  SELECT * FROM v_invoice WHERE service_request_id = p_request_id;
END$$

CREATE PROCEDURE sp_invoice_delete(IN p_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE EXIT HANDLER FOR SQLEXCEPTION
  BEGIN
    ROLLBACK; SET p_problems = 'No se puede eliminar la factura';
  END;
  SET p_problems = NULL;
  IF (SELECT COUNT(*) FROM invoices WHERE id = p_id) = 0 THEN
    SET p_problems = 'Factura no encontrada'; LEAVE p;
  END IF;
  START TRANSACTION;
  DELETE FROM payments WHERE invoice_id = p_id;
  DELETE FROM invoices WHERE id = p_id;
  COMMIT;
END$$

-- ---------------------------------------------------------------- payments

CREATE PROCEDURE sp_payments_by_invoice(IN p_invoice_id BIGINT)
p: BEGIN
  SELECT p.id, p.invoice_id, i.invoice_number, p.amount, p.payment_date, p.payment_method
  FROM payments p JOIN invoices i ON i.id = p.invoice_id
  WHERE p.invoice_id = p_invoice_id
  ORDER BY p.payment_date;
END$$

CREATE PROCEDURE sp_payment_delete(IN p_id BIGINT)
p: BEGIN
  DELETE FROM payments WHERE id = p_id;
END$$

DELIMITER ;
