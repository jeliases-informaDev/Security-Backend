-- =====================================================================================
-- Consultas utiles sobre la base security_db (MySQL). Todas son de SOLO LECTURA.
-- Abre este archivo en MySQL Workbench / DBeaver / IntelliJ (Database) o pega la consulta
-- en phpMyAdmin (http://localhost:8082) y ejecuta una a la vez.
-- Explicacion de cada tabla y como se relacionan: docs/BASE-DE-DATOS.md
-- =====================================================================================


-- 1) ¿Que tablas hay y cuantas filas tienen? (el numero es aproximado)
SELECT table_name AS tabla, table_rows AS filas
FROM information_schema.tables
WHERE table_schema = DATABASE()
ORDER BY table_rows DESC, table_name;


-- 2) TODAS las personas naturales con la lista en la que aparecen (una fila por persona y lista)
SELECT CONCAT_WS(' ', pn.nombre, pn.segundo_nombre, pn.ape_pat, pn.ape_mat) AS persona,
       td.nombre   AS tipo_documento,
       e.documento,
       tl.nombre   AS lista,
       hm.cargo,
       hm.institucion,
       hm.tipo_pep,
       hm.descripcion,
       hm.link
FROM entidades e
JOIN personas_naturales pn ON pn.id_entidades = e.id
JOIN tipo_documento td     ON td.id = e.id_tipo_documento
LEFT JOIN historial_manchas hm ON hm.id_entidades = e.id
LEFT JOIN tipo_lista tl        ON tl.id = hm.id_tipo_lista
ORDER BY persona, lista;


-- 3) Personas Expuestas Politicamente (PEP): politicos, alcaldes, congresistas, etc.
--    Se filtra por el NOMBRE de la lista porque la columna es_pep no esta cargada en todas las bases.
SELECT CONCAT_WS(' ', pn.nombre, pn.segundo_nombre, pn.ape_pat, pn.ape_mat) AS persona,
       e.documento,
       hm.cargo,
       hm.institucion,
       hm.tipo_pep,
       hm.periodo_desde,
       hm.periodo_hasta,
       tl.nombre AS lista
FROM historial_manchas hm
JOIN tipo_lista tl         ON tl.id = hm.id_tipo_lista
JOIN entidades e           ON e.id = hm.id_entidades
JOIN personas_naturales pn ON pn.id_entidades = e.id
WHERE tl.nombre LIKE '%Expuest%'
ORDER BY pn.ape_pat, pn.nombre;


-- 4) Buscar una persona por nombre o apellido (como la pantalla "Listas Negativas").
--    Cambia el texto donde dice "cambia el texto aqui".
SELECT CONCAT_WS(' ', pn.nombre, pn.segundo_nombre, pn.ape_pat, pn.ape_mat) AS persona,
       e.documento,
       tl.nombre AS lista,
       LEFT(hm.descripcion, 80) AS descripcion
FROM (SELECT 'garcia' AS texto) AS q            -- <== cambia el texto aqui
JOIN personas_naturales pn ON UPPER(pn.nombre)         LIKE UPPER(CONCAT('%', q.texto, '%'))
                           OR UPPER(pn.segundo_nombre) LIKE UPPER(CONCAT('%', q.texto, '%'))
                           OR UPPER(pn.ape_pat)        LIKE UPPER(CONCAT('%', q.texto, '%'))
                           OR UPPER(pn.ape_mat)        LIKE UPPER(CONCAT('%', q.texto, '%'))
JOIN entidades e               ON e.id = pn.id_entidades
LEFT JOIN historial_manchas hm ON hm.id_entidades = e.id
LEFT JOIN tipo_lista tl        ON tl.id = hm.id_tipo_lista
ORDER BY persona;


-- 5) Buscar por numero de documento (DNI, RUC, CE o pasaporte).
SELECT e.id, td.nombre AS tipo_documento, e.documento, e.tipo_entidad,
       COALESCE(CONCAT_WS(' ', pn.nombre, pn.ape_pat, pn.ape_mat), pj.razon_social) AS nombre
FROM entidades e
JOIN tipo_documento td          ON td.id = e.id_tipo_documento
LEFT JOIN personas_naturales pn ON pn.id_entidades = e.id
LEFT JOIN personas_juridicas pj ON pj.id_entidades = e.id
WHERE e.documento = '40111201';                  -- <== cambia el documento aqui


-- 6) Empresas (personas juridicas)
SELECT pj.razon_social, e.documento AS ruc, e.departamento, e.provincia, e.distrito, e.rubro
FROM personas_juridicas pj
JOIN entidades e ON e.id = pj.id_entidades
ORDER BY pj.razon_social;


-- 7) ¿Cuantas personas hay en cada lista?
SELECT tl.id, tl.nombre AS lista, lg.nombre AS grupo, COUNT(DISTINCT hm.id_entidades) AS entidades
FROM tipo_lista tl
LEFT JOIN lista_grupo lg       ON lg.id = tl.id_grupo
LEFT JOIN historial_manchas hm ON hm.id_tipo_lista = tl.id
GROUP BY tl.id, tl.nombre, lg.nombre
HAVING entidades > 0
ORDER BY entidades DESC;


-- 8) Historial de busquedas: ¿quien consulto a quien y cuando?
SELECT hc.fecha_consulta,
       u.usuario AS consultado_por,
       COALESCE(CONCAT_WS(' ', pn.nombre, pn.ape_pat, pn.ape_mat), pj.razon_social) AS persona_consultada,
       e.documento
FROM historial_consultas hc
JOIN usuarios u                 ON u.id = hc.id_usuarios
JOIN entidades e                ON e.id = hc.id_entidad
LEFT JOIN personas_naturales pn ON pn.id_entidades = e.id
LEFT JOIN personas_juridicas pj ON pj.id_entidades = e.id
ORDER BY hc.fecha_consulta DESC;


-- 9) Usuarios y su rol (NUNCA se muestra la clave). Los roles reales estan en usuario_roles.
SELECT u.id, u.usuario, u.correo, u.activo, GROUP_CONCAT(r.codigo) AS roles
FROM usuarios u
LEFT JOIN usuario_roles ur ON ur.id_usuario = u.id
LEFT JOIN roles r          ON r.id = ur.id_rol
GROUP BY u.id, u.usuario, u.correo, u.activo
ORDER BY u.id;


-- 10) Matrices de riesgo: analisis guardados con su area y proceso
SELECT a.id, ar.nombre AS area, p.nombre AS proceso, u.usuario AS creado_por
FROM matriz_riesgo_analisis a
LEFT JOIN matriz_riesgo_areas ar    ON ar.id = a.area_id
LEFT JOIN matriz_riesgo_procesos p  ON p.id = a.proceso_id
LEFT JOIN usuarios u                ON u.id = a.id_usuario;


-- 11) Scoring de riesgo guardado
SELECT * FROM scoring_riesgo;
