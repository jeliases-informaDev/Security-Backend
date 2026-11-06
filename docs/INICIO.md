# Tablero de Inicio (página principal)

Módulo `com.security.modules.inicio`. Contrato JSON: `CONTRATO-INICIO.md` (lo tiene quien hace las pantallas). Ninguna ruta
consume tokens.

| Ruta | Quién | Qué hace |
|---|---|---|
| `GET /api/inicio/resumen[?periodo=AAAA-MM]` | toda cuenta autenticada | Cifras del mes (por defecto el mes en curso, hora de Lima). Periodo mal escrito → 400 `INICIO_PERIODO_INVALIDO`. |
| `POST /api/inicio/demo` | solo administrador universal (`ADMINISTRADOR`) | Carga datos de demostración en la empresa de su cuenta. Idempotente (`yaEstaba: true`). |
| `DELETE /api/inicio/demo` | solo administrador universal | Borra solo lo marcado como demostración de esa empresa. |

Sin permiso → 403 `DEMO_SIN_PERMISO`; cuenta universal sin empresa → 400 `DEMO_SIN_EMPRESA`.

## De dónde sale cada cifra

Todo se cuenta y agrupa en la base (`COUNT`, `SUM`, `GROUP BY`). Solo se traen filas para los 3 últimos registros y las fechas
de las denuncias cerradas en el mes (promedio de días). Un bloque que falla se registra en el log y viene `null`; no tumba la página.

**Listas negativas** (`consultas_listas`, solo las consultas `RESUELTA`). Alcance: la empresa guardada en la consulta para el
administrador universal y el de empresa; lo propio para las demás cuentas; el universal sin empresa ve todo.
- `consultas`: del mes. `consultasVariacionPct`: contra el mes anterior completo; `null` si fue 0.
- `conCoincidenciaPct`: clasificación distinta de `SIN_COINCIDENCIAS`. `pepDetectados`: `es_pep`. `homonimosDescartados`: marcadas «Es homónimo» (de las consultas del mes).
- `resultado`: Crítica = clasificación `CRITICA`. **No existe la clasificación «Alta»** (el cálculo solo da Crítica, Media o Sin coincidencias): se toma como Alta la consulta Media con alguna fila del grupo de peso `ALTA` (PEP, ASBANC investigados, GAFI); Media = el resto.
- `porFuente`: consultas con coincidencia por `fuenteNombre` de las filas (columna `listas_coincidencia_texto`), las 5 mayores. Las filas que no están en las reglas cuentan como «Otras listas».
- `criticasSinDecidir`: críticas sin «Es homónimo» **de cualquier fecha** (lo pendiente no caduca con el mes); `masAntiguaDias` desde la más antigua.
- Series: 14 días que terminan hoy (mes en curso) o el último día del mes pedido. Porcentajes enteros (como el contrato).

**Registro de operaciones** (`registro_operaciones_v2`, misma regla de empresa que la lista del módulo: la de la cuenta dueña).
Sin empresa en la cuenta → bloque `null`. Mes = `fecha_registro`.
- `montoTotalUsd`: suma de `monto_usd` (verificación de umbral); si falta y la moneda es USD, el monto. Registros en otra moneda sin tipo de cambio no suman.
- `inusuales`: análisis `EN_ANALISIS` o `SOSPECHOSA`. `sobreUmbral`: `umbral_estado = SI_SE_REGISTRA`. `mismoDiaPct`: `fecha_registro = fecha_operacion`.
- `ultimos`: los 3 más recientes (de cualquier mes); `estado`: `SIN_RESPONSABLE` > `INUSUAL` > `ENVIADO` > `REGISTRADO`.

**Oficial de cumplimiento**: lo mismo que `GET /api/oficial-cumplimiento/estado` (sin empresa → `null`).
- `estado`: el de la empresa; además de los del contrato puede venir **`PENDIENTE_COMUNICAR`** (designado y aún no comunicado a la UIF).
- `alDia`: ningún plazo vencido (comunicación, pendientes, vacancia). `comunicadoUif.enPlazo`: fecha de comunicación ≤ vencimiento de `PLAZO_COMUNICAR_DESIGNACION`.
- `declaraciones`: afirmaciones marcadas de la declaración jurada de requisitos (8 de 8). `alterno`: siempre `null` (alterno «Pronto»).

**Canal de denuncias** (`denuncias`). Alcance como el módulo: universal y administrador de empresa, su empresa (la del receptor o, si no tiene, la de quien registró); supervisor, lo asignado a él; otras cuentas → `null`.
- `recibidas`, `porSituacion`: creadas en el mes. `porAtender` (no cerradas) y `sinAbrir` (`RECIBIDO`): hoy, de cualquier fecha.
- Plazo de atención (docs/DENUNCIAS-CIERRE.md): `plaft.denuncias.plazo-atencion-dias`, 30 días calendario por defecto, **a confirmar con el usuario**. `vencidas`: no cerradas y hoy > creación + plazo. `plazosATiempoPct`: cerradas en el mes con cierre ≤ creación + plazo. `tiempoPromedioCierreDias`: promedio de días (creación → cierre) de las cerradas en el mes.
- `cierres`: reparto en % por `resultadoCierre` de las cerradas en el mes **que tienen resultado** (las cerradas antes de existir el dato no cuentan); `null` si ninguna tiene. `derivadasAlOficial`: cantidad derivada en el mes (por `fechaDerivacion`); el detalle es reservado y nunca sale aquí.
- `porSituacion`: las 6 situaciones con la etiqueta del servidor, de mayor a menor (solo las que tienen denuncias).
- El aislamiento por empresa de denuncias/historial está **diferido** por decisión del usuario: se usa la regla actual sin cambios.

## Datos de demostración

Van a la empresa de la cuenta del administrador universal, con una marca que ningún dato real puede tener:

| Qué | Marca | Cuánto |
|---|---|---|
| Consultas de listas | `criterio_tipo_documento = 'DEMO'` | 312 del mes (72 con coincidencia: 12 críticas, 25 altas, 35 medias; 19 PEP; 11 homónimos; 2 críticas sin decidir, de ayer y de hoy) + 297 del mes anterior |
| Registros de operaciones | `numero_registro = 'RO-DEMO-NNNNNN'` (no mueve el contador RO-AAAA) | 64 del mes (≈ US$ 2,4 M, 19 sobre el umbral, 3 inusuales, 58 el mismo día; los 3 últimos como el diseño) + 30 del mes anterior |
| Denuncias | `codigo_seguimiento = 'DEMO-<hash de la empresa>-NNN'` | 24 recibidas en el mes (situaciones 8/5/4/3/2/2), 5 por atender (2 sin abrir), 1 vencida, 28 cerradas en el mes (82 % a tiempo, 9 días en promedio; 15 con resultado: 40/33/27 %), 2 derivadas al oficial |
| Oficial | designación guardada por `__demo_inicio__` | Mariana Quispe Valdivia, titular vigente, comunicada a la UIF el 2026-03-20; **solo si la empresa no tiene un titular activo** |

El resultado de cada consulta se calcula con `CalculoListas` y las reglas vigentes (el detalle y el PDF se ven completos). Nombres,
documentos y descripciones son inventados. Quitar borra también lo que alguien haya hecho sobre esos datos (historial, análisis,
decisiones, eventos del oficial); los archivos que se hubieran subido a una denuncia de demostración quedan en el almacenamiento.
Si hoy es el día 1, la crítica «de ayer» cae en el mes anterior (el mes en curso muestra una menos).

## Pruebas

`src/test/kotlin/com/security/modules/inicio/InicioIntegracionTest.kt`, contra la MySQL del CI con hoy fijo (06/11/2026): resumen
vacío, carga = ejemplo del contrato, idempotencia, otra empresa no ve nada, quitar deja solo lo real, permisos y periodo inválido.

## KPIs que ya se podrían agregar (datos existentes, no implementados)

Scoring (evaluaciones del mes por nivel de riesgo), matrices de riesgo (riesgo residual por área), plazos con la UIF del oficial
(pendientes por vencer), registros pendientes de envío del mes anterior (exportación «RO a disposición de la UIF») y tokens de la
licencia consumidos en el mes.
