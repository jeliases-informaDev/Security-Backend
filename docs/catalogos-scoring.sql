-- =====================================================================================
-- Catalogos del SCORING de riesgo: ocupaciones y departamentos (con su puntaje 1-5).
--
-- Sin estas filas los combos "Ocupacion / Profesion" y "Departamento (residencia)" de la pantalla
-- Scoring de Riesgo salen VACIOS (y no se puede evaluar). El backend los lee de las tablas
-- scoring_ocupacion y scoring_departamento (endpoint GET /api/scoring/catalogos).
--
-- Como usarlo (a mano, sin tocar codigo):
--   1. Abre tu cliente (phpMyAdmin, MySQL Workbench, DBeaver, IntelliJ) conectado a la base security_db.
--   2. Ejecuta TODO este archivo.
--   3. Recarga la pantalla de Scoring en la web (no hace falta reiniciar el backend).
--
-- Es IDEMPOTENTE: se puede ejecutar varias veces; solo agrega lo que falta y no cambia puntajes existentes.
-- Requiere que exista el pais 'Peru' en la tabla pais (viene en el seed y en las bases del equipo).
-- ATENCION: si la ejecutas sobre la base compartida de Aiven, el cambio lo ve TODO el equipo.
-- =====================================================================================

-- ---- Ocupaciones / profesiones ----
INSERT INTO scoring_ocupacion (nombre, puntaje) VALUES
  ('ABOGADO', 2),
  ('ADMINISTRADOR DE EMPRESAS', 2),
  ('AGRONOMO O AFINES', 1),
  ('ANTROPOLOGO, ARQUEOLOGO, HISTORIADOR', 1),
  ('ARQUITECTO, URBANISTA', 1),
  ('BIBLIOTECARIO, DOCUMENTALISTA', 1),
  ('BIOLOGO', 1),
  ('BOTANICO Y ZOOLOGO', 1),
  ('CONTADOR', 3),
  ('ECONOMISTA', 1),
  ('ENFERMERIA', 1),
  ('INDEPENDIENTE', 4),
  ('SIN EMPLEO', 2),
  ('OTROS', 3)
ON DUPLICATE KEY UPDATE puntaje = puntaje;   -- si ya existe, no hace nada

-- ---- Departamentos de Peru ----
INSERT INTO scoring_departamento (id_pais, nombre, puntaje)
SELECT p.id, v.nombre, v.puntaje
FROM pais p
JOIN (
SELECT 'Amazonas' AS nombre, 1 AS puntaje
  UNION ALL SELECT 'Ancash', 2
  UNION ALL SELECT 'Apurimac', 2
  UNION ALL SELECT 'Arequipa', 1
  UNION ALL SELECT 'Ayacucho', 2
  UNION ALL SELECT 'Cajamarca', 2
  UNION ALL SELECT 'Callao', 2
  UNION ALL SELECT 'Cusco', 2
  UNION ALL SELECT 'Huancavelica', 2
  UNION ALL SELECT 'Huanuco', 2
  UNION ALL SELECT 'Ica', 1
  UNION ALL SELECT 'Junin', 2
  UNION ALL SELECT 'La Libertad', 3
  UNION ALL SELECT 'Lambayeque', 2
  UNION ALL SELECT 'Lima', 2
  UNION ALL SELECT 'Loreto', 2
  UNION ALL SELECT 'Madre de Dios', 1
  UNION ALL SELECT 'Moquegua', 1
  UNION ALL SELECT 'Pasco', 1
  UNION ALL SELECT 'Piura', 3
  UNION ALL SELECT 'Puno', 2
  UNION ALL SELECT 'San Martin', 2
  UNION ALL SELECT 'Tacna', 1
  UNION ALL SELECT 'Tumbes', 1
  UNION ALL SELECT 'Ucayali', 2
) AS v
WHERE p.nombre = 'Peru'
  AND NOT EXISTS (
        SELECT 1 FROM scoring_departamento d
        WHERE d.id_pais = p.id AND d.nombre = v.nombre
  );

-- ---- Comprobacion (debe dar 14 y 25) ----
SELECT (SELECT COUNT(*) FROM scoring_ocupacion)    AS ocupaciones,
       (SELECT COUNT(*) FROM scoring_departamento) AS departamentos;
