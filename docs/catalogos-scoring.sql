-- =====================================================================================
-- Catalogos del SCORING de riesgo: ocupaciones y departamentos (con su puntaje 1-5).
-- Fuente: libro vigente "Scoring y Factores_Riesgo LAFT" (nov-2020). Los mismos valores estan en
-- src/main/resources/scoring/*.csv, que es lo que carga el seed al arrancar con APP_SEED_ENABLED=true.
--
-- Sin estas filas los combos "Ocupacion / Profesion" y "Departamento (residencia)" de la pantalla
-- Scoring de Riesgo salen VACIOS (y no se puede evaluar). El backend los lee de scoring_ocupacion y
-- scoring_departamento (endpoint GET /api/scoring/catalogos).
--
-- Como usarlo (a mano, sin tocar codigo):
--   1. Abre tu cliente (phpMyAdmin, MySQL Workbench, DBeaver, IntelliJ) conectado a la base security_db.
--   2. Ejecuta TODO este archivo.
--   3. Recarga la pantalla de Scoring en la web (no hace falta reiniciar el backend).
--
-- Es IDEMPOTENTE: se puede ejecutar varias veces. AGREGA lo que falta y CORRIGE los puntajes que difieran
-- del libro (no borra nada ni toca otras tablas). Requiere el pais 'Peru' en la tabla pais.
-- ATENCION: si la ejecutas sobre la base compartida de Aiven, el cambio lo ve TODO el equipo, y las
-- evaluaciones nuevas usaran estos puntajes (las ya guardadas conservan su puntaje total).
-- =====================================================================================

-- ---- Ocupaciones / profesiones (143) ----
INSERT INTO scoring_ocupacion (nombre, puntaje) VALUES
  ('ABOGADO', 2),
  ('ADMINISTRADOR DE EMPRESAS', 2),
  ('AGRONOMO O AFINES', 1),
  ('ANTROPOLOGO, ARQUEOLOGO, HISTORIADOR', 1),
  ('ARCHIVERO', 1),
  ('ARQUITECTO, URBANISTA', 1),
  ('BIBLIOTECARIO, DOCUMENTALISTA', 1),
  ('BIOLOGO', 1),
  ('BOTANICO Y ZOOLOGO', 1),
  ('CONTADOR', 3),
  ('DERECHO DE LAS CIENCIAS ECONOM', 1),
  ('DOCTOR', 1),
  ('ECONOMISTA', 1),
  ('ENFERMERIA', 1),
  ('ESCRITOR, ARTISTA CREATIVOS', 1),
  ('ESPECIALISTA EN COOPERATIVISMO', 1),
  ('FARMACEUTICO', 1),
  ('FARMACOLOGO, PATOLOGO Y AFINES', 1),
  ('FILOLOGO, TRADUCTOR O INTERPRE', 1),
  ('FILOSOFO O ESPECIALISTA EN CIE', 1),
  ('FISICO', 1),
  ('FUERZAS ARMADAS', 1),
  ('GEOGRAFO', 1),
  ('GEOLOGO, GEOFISICO O OCEANOGRA', 1),
  ('INGENIERO CIVIL', 2),
  ('INGENIERO DE MINAS, METALURGI', 2),
  ('INGENIERO DE SISTEMA, CREADOR', 2),
  ('INGENIERO ELECTRICISTA, ELECTR', 2),
  ('INGENIERO ESTADISTICO, ESTADIS', 2),
  ('INGENIERO INDUSTRIAL', 2),
  ('INGENIERO MECANICO', 2),
  ('INGENIERO PESQUERO', 2),
  ('INGENIERO QUIMICO', 2),
  ('MATEMATICO O AFINES', 1),
  ('METEOROLOGO O COSMOGRAFO', 1),
  ('MICROBIOLOGO, BACTERIOLOGO', 1),
  ('NOTARIO', 2),
  ('NUTRICIONISTA, DIETISTA O BROM', 1),
  ('OBSTETRIZ', 1),
  ('ODONTOLOGO', 1),
  ('OFICIO', 1),
  ('OTRA INGENIERIA', 2),
  ('OTRAS PROFESIONES', 3),
  ('PROFESIONAL DEL TRABAJO SOCIAL', 1),
  ('PROFESOR(MAESTRO Y/O PEDAGOGO', 2),
  ('PSICOLOGO', 1),
  ('QUIMICO', 2),
  ('SACERDOTE O RELIGIOSO', 1),
  ('TECNICO', 1),
  ('VETERINARIO', 1),
  ('SIN PROFESION', 1),
  ('ACTOR, ACTRIZ, ARTISTA, DIREC', 1),
  ('ACTUARIO', 1),
  ('ADUANERO/AGENTE DE ADUANAS', 1),
  ('AEROMOZO/ AZAFATA', 1),
  ('AGENTE / INTERMEDIARIO / CO', 2),
  ('AGENTE DE BOLSA', 3),
  ('AGENTE DE INMIGRACION/MIGRA', 2),
  ('AGENTE DE TURISMO/VIAJES', 2),
  ('AGENTE/INTERMEDIARIO/CORREDO', 2),
  ('ALBANIL, OBRERO DE CONSTRUCC', 1),
  ('AMA DE CASA', 2),
  ('ANALISTAS DE SISTEMA Y COMPU', 1),
  ('ARMADOR DE BARCO', 1),
  ('ARTESANO', 1),
  ('AVICULTOR', 1),
  ('BASURERO / BARRENDERO', 1),
  ('CAJERO', 1),
  ('CAMARERO / BARMAN / MESERO/ C', 1),
  ('CAMBISTA, COMPRA/VENTA DE MON', 1),
  ('CAMPESINO', 1),
  ('CAPATAZ', 1),
  ('CARGADOR', 1),
  ('CARPINTERO', 1),
  ('CARTERO', 1),
  ('CERRAJERO', 1),
  ('COBRADOR', 1),
  ('COMERCIANTE / VENDEDOR', 3),
  ('CONDUCTOR, CHOFER / TAXISTA', 1),
  ('CONSERJE / PORTERO/ GUARDIAN', 1),
  ('CONSTRUCTOR', 1),
  ('CONTRATISTA', 2),
  ('CORTE Y CONFECCION DE ROPA/F', 1),
  ('COSMETOLOGO, PELUQUERO Y BARB', 1),
  ('DECORADOR, DIBUJANTE, PUBLICI', 1),
  ('DEPORTISTA PROFESIONAL, ATLET', 1),
  ('DISTRIBUIDOR', 1),
  ('ELECTRICISTA', 1),
  ('EMPLEADA (O) DEL HOGAR / NANA', 1),
  ('EMPRESARIO EXPORTADOR/ EMPRESA', 3),
  ('ENSAMBLADOR', 1),
  ('ESCULTOR', 1),
  ('ESTUDIANTE', 2),
  ('GANADERO', 1),
  ('GASFITERO', 1),
  ('HISTORIADOR', 1),
  ('JARDINERO', 1),
  ('JOCKEY', 1),
  ('JOYERO Y/O PLATERO / ORFEBR', 3),
  ('JUBILADO / PENSIONISTA', 2),
  ('LABORATORISTA (TÉCNICO)', 1),
  ('LIQUIDADOR, RECLAMACIONES/SE', 1),
  ('MAQUINISTA / OPERADOR DE MAQ', 1),
  ('MARTILLERO / SUBASTADOR,', 1),
  ('MAYORISTA, COMERCIO AL POR M', 1),
  ('MECANICO', 1),
  ('METALURGISTA', 1),
  ('ORGANIZADOR DE EVENTOS', 1),
  ('PANADERO / PASTELERO', 1),
  ('PARAMEDICO', 1),
  ('PERIODISTA', 2),
  ('PERITO', 2),
  ('PESCADOR', 1),
  ('PILOTO', 1),
  ('PINTOR', 1),
  ('POLICIA MUNICIPAL', 2),
  ('POLICIA PNP', 2),
  ('PRODUCTOR DE CINE / RADIO / T', 3),
  ('PRODUCTOR, CULTIVOS EXTENSIVO', 3),
  ('PROGRAMADOR', 1),
  ('QUIROPRACTICO/ KINESITERAPEUT', 1),
  ('RELACIONISTA PÚBLICO E INDUST', 1),
  ('RELOJERO', 1),
  ('REPARACION DE AUTOMOVILES,PIN', 1),
  ('REPARADOR DE APARATOS ELECTRO', 1),
  ('REPARTIDOR', 1),
  ('SECRETARIA, RECEPCIONISTA, TE', 1),
  ('SEGURIDAD / GUARDAESPALDA / G', 1),
  ('SERVICIO DE ALMACENAMIENTO/AL', 1),
  ('SERVICIO DE ALQUILER DE VEHIC', 1),
  ('SERVICIO DE ALQUILER DE VIDEO', 1),
  ('SOCIOLOGO', 1),
  ('TASADOR', 1),
  ('TORERO', 1),
  ('TRAMITADOR', 1),
  ('TRANSPORTE DE CARGA Y/O MUDANZ', 2),
  ('TRANSPORTISTA', 2),
  ('VENDEDOR AMBULANTE', 1),
  ('VISITADOR MÉDICO', 1),
  ('ZAPATERO', 1),
  ('INDEPENDIENTE', 3),
  ('SIN EMPLEO', 2),
  ('OTROS', 4)
ON DUPLICATE KEY UPDATE puntaje = VALUES(puntaje);   -- si ya existe, deja el puntaje del libro

-- ---- Departamentos de Peru (25) ----
INSERT INTO scoring_departamento (id_pais, nombre, puntaje)
SELECT p.id, v.nombre, v.puntaje
FROM pais p
JOIN (
SELECT 'Amazonas' AS nombre, 2 AS puntaje
  UNION ALL SELECT 'Ancash', 2
  UNION ALL SELECT 'Apurimac', 2
  UNION ALL SELECT 'Arequipa', 3
  UNION ALL SELECT 'Ayacucho', 2
  UNION ALL SELECT 'Cajamarca', 2
  UNION ALL SELECT 'Callao', 2
  UNION ALL SELECT 'Cusco', 2
  UNION ALL SELECT 'Huancavelica', 2
  UNION ALL SELECT 'Huanuco', 2
  UNION ALL SELECT 'Ica', 1
  UNION ALL SELECT 'Junin', 2
  UNION ALL SELECT 'La Libertad', 4
  UNION ALL SELECT 'Lambayeque', 2
  UNION ALL SELECT 'Lima', 2
  UNION ALL SELECT 'Loreto', 2
  UNION ALL SELECT 'Madre de Dios', 2
  UNION ALL SELECT 'Moquegua', 2
  UNION ALL SELECT 'Pasco', 1
  UNION ALL SELECT 'Piura', 5
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

-- Corrige los puntajes de los departamentos que ya existian y no coinciden con el libro
UPDATE scoring_departamento d
JOIN pais p ON p.id = d.id_pais AND p.nombre = 'Peru'
JOIN (
SELECT 'Amazonas' AS nombre, 2 AS puntaje
  UNION ALL SELECT 'Ancash', 2
  UNION ALL SELECT 'Apurimac', 2
  UNION ALL SELECT 'Arequipa', 3
  UNION ALL SELECT 'Ayacucho', 2
  UNION ALL SELECT 'Cajamarca', 2
  UNION ALL SELECT 'Callao', 2
  UNION ALL SELECT 'Cusco', 2
  UNION ALL SELECT 'Huancavelica', 2
  UNION ALL SELECT 'Huanuco', 2
  UNION ALL SELECT 'Ica', 1
  UNION ALL SELECT 'Junin', 2
  UNION ALL SELECT 'La Libertad', 4
  UNION ALL SELECT 'Lambayeque', 2
  UNION ALL SELECT 'Lima', 2
  UNION ALL SELECT 'Loreto', 2
  UNION ALL SELECT 'Madre de Dios', 2
  UNION ALL SELECT 'Moquegua', 2
  UNION ALL SELECT 'Pasco', 1
  UNION ALL SELECT 'Piura', 5
  UNION ALL SELECT 'Puno', 2
  UNION ALL SELECT 'San Martin', 2
  UNION ALL SELECT 'Tacna', 1
  UNION ALL SELECT 'Tumbes', 1
  UNION ALL SELECT 'Ucayali', 2
) AS v ON v.nombre = d.nombre
SET d.puntaje = v.puntaje
WHERE d.puntaje <> v.puntaje;

-- ---- Comprobacion (debe dar 143 y 25) ----
SELECT (SELECT COUNT(*) FROM scoring_ocupacion)    AS ocupaciones,
       (SELECT COUNT(*) FROM scoring_departamento) AS departamentos;
