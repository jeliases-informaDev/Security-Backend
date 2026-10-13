-- Ejecutar primero operaciones-esquema-v2.sql.
-- Ejecutar este archivo completo en la MISMA conexion.
-- Si ocurre un error, detener el script y ejecutar ROLLBACK.
-- Datos ficticios; cada ejecucion agrega 10 registros nuevos.
USE security_db;
SET @demo_lote = REPLACE(UUID(), '-', '');
SET @demo_usuario = (SELECT MIN(id) FROM usuarios);
SELECT IF(@demo_usuario IS NULL, 'Falta un usuario: no se insertaran filas', 'Listo') AS estado;
START TRANSACTION;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-0'), 'Ana', 'Torres Vega', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-0'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-01', 'Lima', 'Transferencia', 'PEN', 1000, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-1'), @demo_usuario, 'Empresa Demo', 'Lima', '2026-10-01', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Ana Torres Vega', 'DEMO', CONCAT(@demo_lote, '-B-0'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-1'), 'Luis', 'Ramirez Soto', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-1'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-02', 'Miraflores', 'Transferencia', 'PEN', 1250, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-2'), @demo_usuario, 'Empresa Demo', 'Miraflores', '2026-10-02', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Luis Ramirez Soto', 'DEMO', CONCAT(@demo_lote, '-B-1'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-2'), 'Carla', 'Mendoza Ruiz', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-2'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-03', 'San Isidro', 'Transferencia', 'PEN', 1500, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-3'), @demo_usuario, 'Empresa Demo', 'San Isidro', '2026-10-03', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Carla Mendoza Ruiz', 'DEMO', CONCAT(@demo_lote, '-B-2'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-3'), 'Diego', 'Flores Castro', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-3'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-04', 'Arequipa', 'Transferencia', 'PEN', 1750, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-4'), @demo_usuario, 'Empresa Demo', 'Arequipa', '2026-10-04', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Diego Flores Castro', 'DEMO', CONCAT(@demo_lote, '-B-3'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-4'), 'Elena', 'Vargas Rios', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-4'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-05', 'Trujillo', 'Transferencia', 'PEN', 2000, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-5'), @demo_usuario, 'Empresa Demo', 'Trujillo', '2026-10-05', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Elena Vargas Rios', 'DEMO', CONCAT(@demo_lote, '-B-4'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-5'), 'Jorge', 'Quispe Leon', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-5'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-06', 'Cusco', 'Transferencia', 'PEN', 2250, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-6'), @demo_usuario, 'Empresa Demo', 'Cusco', '2026-10-06', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Jorge Quispe Leon', 'DEMO', CONCAT(@demo_lote, '-B-5'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-6'), 'Lucia', 'Herrera Diaz', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-6'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-07', 'Piura', 'Transferencia', 'PEN', 2500, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-7'), @demo_usuario, 'Empresa Demo', 'Piura', '2026-10-07', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Lucia Herrera Diaz', 'DEMO', CONCAT(@demo_lote, '-B-6'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-7'), 'Marco', 'Salazar Cruz', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-7'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-08', 'Chiclayo', 'Transferencia', 'PEN', 2750, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-8'), @demo_usuario, 'Empresa Demo', 'Chiclayo', '2026-10-08', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Marco Salazar Cruz', 'DEMO', CONCAT(@demo_lote, '-B-7'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-8'), 'Rosa', 'Medina Paredes', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-8'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-09', 'Huancayo', 'Transferencia', 'PEN', 3000, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-9'), @demo_usuario, 'Empresa Demo', 'Huancayo', '2026-10-09', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Rosa Medina Paredes', 'DEMO', CONCAT(@demo_lote, '-B-8'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-F-9'), 'Pedro', 'Rojas Silva', '1990-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_fisica = LAST_INSERT_ID();
INSERT INTO personas_operacion (tipo_documento, numero_documento, nombres, apellidos, fecha_nacimiento)
SELECT 'DEMO', CONCAT(@demo_lote, '-R-9'), 'Gabriela', 'Demo Representante', '1985-01-01' WHERE @demo_usuario IS NOT NULL;
SET @demo_representada = LAST_INSERT_ID();
INSERT INTO operaciones_v2 (fecha_operacion, lugar, modalidad_pago, moneda, monto, tipo_operacion)
SELECT '2026-10-10', 'Tacna', 'Transferencia', 'PEN', 3250, 'Deposito' WHERE @demo_usuario IS NOT NULL;
SET @demo_operacion = LAST_INSERT_ID();
INSERT INTO registro_operaciones_v2 (numero_registro, id_usuario, empresa, oficina, fecha_registro, id_persona_fisica, id_persona_representada, id_operacion)
SELECT CONCAT('DEMO-', @demo_lote, '-10'), @demo_usuario, 'Empresa Demo', 'Tacna', '2026-10-10', @demo_fisica, @demo_representada, @demo_operacion WHERE @demo_usuario IS NOT NULL;
SET @demo_registro = LAST_INSERT_ID();
INSERT INTO beneficiarios_operacion_v2 (registro_id, nombre, tipo_documento, numero_documento, fecha_nacimiento)
SELECT @demo_registro, 'Pedro Rojas Silva', 'DEMO', CONCAT(@demo_lote, '-B-9'), '1990-01-01' WHERE @demo_usuario IS NOT NULL;

COMMIT;
SELECT r.id, r.numero_registro, CONCAT_WS(', ', p.apellidos, p.nombres) AS personaFisica,
       CONCAT_WS(', ', pr.apellidos, pr.nombres) AS personaNombre, r.oficina, r.fecha_registro
FROM registro_operaciones_v2 r
JOIN personas_operacion p ON p.id = r.id_persona_fisica
LEFT JOIN personas_operacion pr ON pr.id = r.id_persona_representada
WHERE r.numero_registro LIKE CONCAT('DEMO-', @demo_lote, '-%')
ORDER BY r.fecha_registro DESC, r.id DESC;
