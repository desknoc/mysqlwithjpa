-- Test mirror of the DBA-owned production `usuario` DDL (Aiven MySQL 8.4),
-- including its three triggers. Hibernate never creates or alters this schema
-- (ddl-auto=validate); this script is the source of truth for test databases.
--
-- Loaded via spring.sql.init with separator `^` (spring.sql.init.separator=^)
-- so the compound IF...END IF body of `validarContrasena` parses as a single
-- statement. All statements therefore terminate with `^`, never `;`.
--
-- Column sizes use varchar(255) so Hibernate's validate-time type expectations
-- match the mapping defaults; datetime(6) matches Hibernate's LocalDateTime type.
-- `rol` intentionally has NO column default: trigger `rolDefecto` supplies
-- 'USUARIO' when the application omits the column on insert (@DynamicInsert).
-- `fecha_registro` has no DB default here: the application sets it on insert
-- (open question on the production DDL DEFAULT is tracked for Phase 7.5).

CREATE TABLE usuario (
  id_usuario INT NOT NULL AUTO_INCREMENT,
  primer_nombre VARCHAR(255) NOT NULL,
  segundo_nombre VARCHAR(255) NULL,
  primer_apellido VARCHAR(255) NOT NULL,
  segundo_apellido VARCHAR(255) NULL,
  tipo_documento ENUM('CC','TI') NOT NULL,
  documento BIGINT NOT NULL,
  celular VARCHAR(255) NULL,
  grupo_formacion VARCHAR(255) NULL,
  correo_electronico VARCHAR(255) NOT NULL,
  contrasena VARCHAR(255) NOT NULL,
  rol ENUM('ADMIN','USUARIO') NOT NULL,
  tipo_apoyo ENUM('regular','alimentacion','transporte') NULL,
  fecha_registro DATETIME(6) NOT NULL,
  ultima_actualizacion DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uk_usuario_documento (documento),
  UNIQUE KEY uk_usuario_correo (correo_electronico)
)^

CREATE TRIGGER rolDefecto BEFORE INSERT ON usuario
  FOR EACH ROW
  SET NEW.rol = COALESCE(NEW.rol, 'USUARIO')^

CREATE TRIGGER actualizarFechaUsuario BEFORE UPDATE ON usuario
  FOR EACH ROW
  SET NEW.ultima_actualizacion = NOW()^

CREATE TRIGGER validarContrasena BEFORE INSERT ON usuario
  FOR EACH ROW
  IF CHAR_LENGTH(NEW.contrasena) < 8 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'contrasena must be at least 8 characters';
  END IF^
