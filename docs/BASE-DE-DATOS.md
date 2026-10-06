# Base de datos del backend (`security_db`, MySQL)

Esta guía responde: **¿qué base es, dónde está, cómo la veo y qué hay en cada tabla?**
Las consultas listas para ejecutar están en [consultas.sql](consultas.sql).

> Las personas públicas de la política con sus **casos en prensa** NO están aquí: viven en el microservicio de ML (otra base, Postgres). Ver `docs/BASE-DE-DATOS.md` del repositorio **Security-MachineLearning**.

---

## 1. ¿Cuál es y dónde está?

La base se llama **`security_db`** (MySQL 8). Dónde está depende de cómo levantes el backend:

| Cómo lo levantas | Dónde está la base | Qué contiene |
|---|---|---|
| `docker compose up -d` en este repo | Contenedor `mysql-db` → `localhost:3307`, usuario `root`, clave `root` | Base **nueva y propia**: solo los datos iniciales (3 roles, usuario `superadmin`, tipos de documento y 5 países). Sin personas en listas. |
| `.env` con `DB_URL` de **Aiven** | MySQL compartida en la nube (host y puerto en la consola de Aiven; usuario y clave los da el responsable) | Base compartida del equipo con datos de demostración. **Sus catálogos de scoring están vacíos** (ver sección 5). |
| Una base restaurada de un volcado | Donde la restaures | Lo que traiga el volcado. |

Para saber a cuál apunta **tu** backend, mira `DB_URL` en tu `.env` (si no tienes `.env`, usa `localhost:3307`, la del compose).

> ⚠️ **El contenedor MySQL de Docker y Aiven son bases distintas.** Ver una en phpMyAdmin no significa que la web muestre esos datos: la web muestra la que indique `DB_URL`.

Las tablas las crea Hibernate al arrancar (`ddl-auto: update`); no hay migraciones versionadas.

## 2. Cómo ver las tablas y su contenido

| Herramienta | Cómo conectarte |
|---|---|
| **phpMyAdmin** (incluido en el compose) | http://localhost:8082 → servidor `mysql-db`, usuario `root`, clave `root` → base `security_db` |
| **MySQL Workbench / DBeaver / IntelliJ (Database)** | Host `localhost` · Puerto `3307` · Usuario `root` · Clave `root` · Base `security_db`. (Para Aiven: host y puerto de la consola, usuario y clave del responsable, **SSL requerido**.) |
| **Terminal** | `docker compose exec mysql-db mysql -uroot -proot security_db` y luego `SHOW TABLES;` |

Luego abre [consultas.sql](consultas.sql) y ejecuta una consulta a la vez.

## 3. Mapa de tablas

### Listas negativas y PEP (lo que busca la pantalla *Listas Negativas*)

```
 tipo_documento ─┐
 pais ───────────┤
                 ▼
            entidades  ◄─────────── historial_consultas ──► usuarios   (quién buscó a quién)
         (persona o empresa)
          │  │
          │  └──► personas_juridicas   (razon_social)            1 a 1
          └─────► personas_naturales   (nombre, apellidos…)      1 a 1
          │
          └─────► historial_manchas ──► tipo_lista ──► lista_grupo
                  (en qué lista aparece, con cargo, institución, enlace…)
```

| Tabla | Qué guarda |
|---|---|
| `entidades` | La persona o empresa: documento, tipo de entidad, ubicación, rubro, alias. Es el centro; todo cuelga de aquí. |
| `personas_naturales` | Nombre, segundo nombre, apellidos, sexo, pasaporte. 1 fila por entidad que sea persona. |
| `personas_juridicas` | `razon_social`. 1 fila por entidad que sea empresa. |
| `historial_manchas` | Cada vez que una entidad **aparece en una lista**: lista, descripción, enlace, fecha, y para PEP: `cargo`, `institucion`, `periodo_desde/hasta`, `tipo_pep`. |
| `tipo_lista` | Catálogo de listas (p. ej. *Personas Expuestas Politicamente*, *Actos Ilicitos*, *Noticias*, *Listas Internacionales*). |
| `lista_grupo` | Agrupa las listas y les da color/orden. |
| `historial_consultas` | Auditoría: qué usuario consultó a qué entidad y cuándo. |
| `tipo_documento` | DNI, RUC, CE, PASAPORTE. |
| `pais` | Países. |

**Cómo se busca una persona:** la web llama a `GET /api/listas-negativas/buscar` → el backend filtra `entidades` por documento exacto y/o nombre/apellidos (`LIKE`) → registra la búsqueda en `historial_consultas`.

**Dónde están los políticos aquí:** son las entidades cuya lista es *Personas Expuestas Politicamente* (consulta 3 de `consultas.sql`), con su `cargo` e `institucion`. Se filtra por el nombre de la lista porque la columna `tipo_lista.es_pep` no está cargada en todas las bases.

### Usuarios y acceso

| Tabla | Qué guarda |
|---|---|
| `usuarios` | Usuario, **clave en BCrypt** (nunca en texto plano), correo, nombres, `activo`. |
| `roles` | `ADMINISTRADOR`, `SUPERVISOR`, `USUARIO`. |
| `usuario_roles` | Qué rol tiene cada usuario. **Esta** es la relación que usa el sistema. |
| `password_reset_tokens` | Tokens para recuperar la contraseña. |

`usuarios.id_rol` es un resto del diseño anterior: el código ya no lo usa (puede venir `NULL`).

### Matrices de riesgo

`matriz_riesgo_areas` y `matriz_riesgo_procesos` (catálogos del usuario) → `matriz_riesgo_area_proceso` (qué proceso pertenece a qué área) → `matriz_riesgo_analisis` (la evaluación guardada).

### Scoring

`scoring_departamento` (por país) y `scoring_ocupacion` son catálogos con un puntaje de 1 a 5; `scoring_riesgo` guarda cada evaluación (entidad + departamento + ocupación + usuario).

**Los combos "Ocupación / Profesión" y "Departamento" de la pantalla *Scoring de Riesgo* se llenan con esas dos tablas** (`GET /api/scoring/catalogos`). Si salen vacíos, las tablas están vacías en la base a la que apunta el backend (ver sección 5).

### Tablas sin código todavía (módulos pendientes)

Existen en las bases del equipo (se crearon con un script SQL) pero **el backend aún no las usa y una base nueva del compose no las trae**: `accesos_usuario`, `base_programada`, `beneficiarios_operacion`, `canal_denuncias`, `extension_judicial`, `extension_natural`, `galeria`, `membresia`, `monedas`, `notificaciones`, `planes`, `registro_operaciones`, `rol_rutas`, `rutas`, `token`, `tokens_consulta`. Corresponden a los módulos de denuncias, operaciones, cursos y planes.

## 4. Cómo tener datos para consultar

Una base nueva del compose trae solo los datos iniciales; **no incluye personas en listas**. Opciones:

1. **Apuntar a la base compartida de Aiven** (solo lectura recomendada): `DB_URL` en `.env`. Ojo: con `ddl-auto: update` cada versión de código modifica el esquema de todos.
2. **Restaurar un volcado**: pídele al responsable un `mysqldump` de la base y cárgalo con
   `docker compose exec -T mysql-db mysql -uroot -proot security_db < volcado.sql`.
3. **Crear registros a mano** desde phpMyAdmin o desde la API (`/swagger-ui.html`).

> Esta carpeta no incluye datos de ejemplo. Si el equipo quiere una carga de demostración versionada, hay que acordar qué datos pueden vivir en el repositorio.

## 5. Problema frecuente: los combos de Scoring salen vacíos

**Síntoma:** en *Scoring de Riesgo*, los desplegables "Ocupación / Profesión" y "Departamento" solo muestran "Selecciona…" y el botón *Evaluar riesgo* no se habilita.

**Causa:** no es un problema de conexión. Las tablas `scoring_ocupacion` y `scoring_departamento` están **vacías** en la base a la que apunta el backend. Compruébalo con:

```sql
SELECT (SELECT COUNT(*) FROM scoring_ocupacion) AS ocupaciones,
       (SELECT COUNT(*) FROM scoring_departamento) AS departamentos;   -- debe dar 14 y 25
```

**Solución (elige una):**

| Opción | Cuándo |
|---|---|
| **Nada que hacer** | Si usas el `docker compose` de este repo: el arranque ya carga los catálogos (`APP_SEED_ENABLED=true`). |
| **Ejecutar [catalogos-scoring.sql](catalogos-scoring.sql) a mano** | En cualquier otra base (Aiven, una restaurada…). Ábrelo en phpMyAdmin / Workbench / DBeaver conectado a `security_db`, ejecútalo completo y recarga la web. Es repetible: no duplica ni cambia puntajes. **Sobre Aiven lo ve todo el equipo.** |
| **Apuntar el backend a otra base que ya tenga catálogos** | Cambia `DB_URL` en `.env` y reinicia el backend. |
