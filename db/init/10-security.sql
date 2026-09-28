-- ================================================================ routines
-- Users, roles, permissions and the audit trail.

USE sysportdb;

DELIMITER $$

-- ---------------------------------------------------------------- security

CREATE PROCEDURE sp_user_by_username(IN p_username VARCHAR(50))
p: BEGIN
  SELECT u.id, u.employee_id, u.username, u.password_hash, u.role_id,
         r.name AS role_name, u.status
  FROM users u JOIN roles r ON r.id = u.role_id
  WHERE u.username = p_username;
END$$

CREATE PROCEDURE sp_user_by_id(IN p_id BIGINT)
p: BEGIN
  SELECT u.id, u.employee_id, u.username, u.password_hash, u.role_id,
         r.name AS role_name, u.status
  FROM users u JOIN roles r ON r.id = u.role_id
  WHERE u.id = p_id;
END$$

CREATE PROCEDURE sp_users_list()
p: BEGIN
  SELECT u.id, u.employee_id, u.username, u.password_hash, u.role_id,
         r.name AS role_name, u.status
  FROM users u JOIN roles r ON r.id = u.role_id
  ORDER BY u.username;
END$$

CREATE PROCEDURE sp_roles_list()
p: BEGIN
  SELECT id, name FROM roles ORDER BY name;
END$$

CREATE PROCEDURE sp_role_permissions(IN p_role_id BIGINT)
p: BEGIN
  SELECT p.name
  FROM role_permissions rp JOIN permissions p ON p.id = rp.permission_id
  WHERE rp.role_id = p_role_id
  ORDER BY p.name;
END$$

CREATE PROCEDURE sp_user_insert(IN p_username VARCHAR(50), IN p_hash VARCHAR(100),
    IN p_role_id BIGINT, IN p_employee_id BIGINT, IN p_status VARCHAR(20), OUT p_id BIGINT)
p: BEGIN
  CALL sp_next_id('users', p_id);
  INSERT INTO users (id, username, password_hash, role_id, employee_id, status)
  VALUES (p_id, p_username, p_hash, p_role_id, p_employee_id, COALESCE(p_status, 'active'));
END$$

CREATE PROCEDURE sp_user_update(IN p_id BIGINT, IN p_username VARCHAR(50),
    IN p_role_id BIGINT, IN p_employee_id BIGINT, IN p_status VARCHAR(20))
p: BEGIN
  UPDATE users SET username = p_username, role_id = p_role_id,
                   employee_id = p_employee_id, status = COALESCE(p_status, 'active')
  WHERE id = p_id;
END$$

CREATE PROCEDURE sp_user_update_password(IN p_id BIGINT, IN p_hash VARCHAR(100))
p: BEGIN
  UPDATE users SET password_hash = p_hash WHERE id = p_id;
END$$

-- BR-27: a user cannot delete itself and the last active administrator is
-- protected, so the system can never be locked out.
CREATE PROCEDURE sp_user_delete(IN p_id BIGINT, IN p_actor_id BIGINT, OUT p_problems TEXT)
p: BEGIN
  DECLARE v_role VARCHAR(50) DEFAULT NULL;
  DECLARE v_admins INT DEFAULT 0;
  SET p_problems = NULL;
  SELECT r.name INTO v_role
  FROM users u JOIN roles r ON r.id = u.role_id
  WHERE u.id = p_id;
  IF v_role IS NULL THEN
    SET p_problems = 'Usuario no encontrado'; LEAVE p;
  END IF;
  IF p_id = p_actor_id THEN
    SET p_problems = 'No puede eliminar su propio usuario'; LEAVE p;
  END IF;
  IF v_role = 'admin' THEN
    SELECT COUNT(*) INTO v_admins
    FROM users u JOIN roles r ON r.id = u.role_id
    WHERE r.name = 'admin' AND u.status = 'active';
    IF v_admins <= 1 THEN
      SET p_problems = 'No se puede eliminar el ultimo administrador'; LEAVE p;
    END IF;
  END IF;
  DELETE FROM users WHERE id = p_id;
END$$

CREATE PROCEDURE sp_audit_log(IN p_user_id BIGINT, IN p_entity VARCHAR(50),
    IN p_entity_id BIGINT, IN p_action VARCHAR(50), IN p_details VARCHAR(500))
p: BEGIN
  DECLARE v_id BIGINT;
  CALL sp_next_id('audit_log', v_id);
  INSERT INTO audit_log (id, user_id, entity, entity_id, action, details)
  VALUES (v_id, p_user_id, p_entity, p_entity_id, p_action, p_details);
END$$

DELIMITER ;
