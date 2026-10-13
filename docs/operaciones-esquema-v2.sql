-- MySQL 8. Ejecutar antes de iniciar el backend actualizado.
-- Crea tablas nuevas; no borra ni migra registros historicos.
-- DDL en MySQL hace COMMIT implicito.
USE security_db;

CREATE TABLE IF NOT EXISTS personas_operacion (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tipo_documento VARCHAR(50), numero_documento VARCHAR(50),
 nombres VARCHAR(150), apellidos VARCHAR(150), fecha_nacimiento DATE,
 nacionalidad VARCHAR(100), telefono VARCHAR(50), profesion_ocupacion VARCHAR(150),
 domicilio TEXT, codigo_postal VARCHAR(20), departamento VARCHAR(100),
 provincia VARCHAR(100), distrito VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operaciones_v2 (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 fecha_operacion DATE NOT NULL, lugar VARCHAR(255),
 modalidad_pago VARCHAR(255) NOT NULL, moneda VARCHAR(255) NOT NULL,
 monto DECIMAL(15,2) NOT NULL, tipo_operacion VARCHAR(255) NOT NULL,
 nro_cuenta_1 VARCHAR(255), nro_cuenta_2 VARCHAR(255), nro_cuenta_3 VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS registro_operaciones_v2 (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 numero_registro VARCHAR(50) NOT NULL UNIQUE,
 id_usuario INT NOT NULL, empresa VARCHAR(255) NOT NULL, oficina VARCHAR(255) NOT NULL,
 fecha_registro DATE NOT NULL, fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 id_persona_fisica BIGINT NOT NULL UNIQUE,
 id_persona_representada BIGINT UNIQUE,
 id_operacion BIGINT NOT NULL UNIQUE,
 INDEX idx_reg_v2_fecha (fecha_registro, id),
 CONSTRAINT fk_reg_v2_usuario FOREIGN KEY (id_usuario) REFERENCES usuarios(id),
 CONSTRAINT fk_reg_v2_fisica FOREIGN KEY (id_persona_fisica) REFERENCES personas_operacion(id),
 CONSTRAINT fk_reg_v2_representada FOREIGN KEY (id_persona_representada) REFERENCES personas_operacion(id),
 CONSTRAINT fk_reg_v2_operacion FOREIGN KEY (id_operacion) REFERENCES operaciones_v2(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS beneficiarios_operacion_v2 (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 registro_id BIGINT,
 nombre VARCHAR(255) NOT NULL, tipo_documento VARCHAR(255) NOT NULL,
 numero_documento VARCHAR(255) NOT NULL, fecha_nacimiento DATE NOT NULL,
 CONSTRAINT fk_benef_v2_registro FOREIGN KEY (registro_id) REFERENCES registro_operaciones_v2(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
