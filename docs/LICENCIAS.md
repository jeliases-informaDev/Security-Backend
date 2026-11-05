# Licencias de PLAFT

PLAFT no lleva la cuenta de ninguna licencia. La única verdad es el **servicio común de licencias de Sulotec**: PLAFT solo pregunta, pide permiso para consumir tokens y muestra el estado. Es la misma lógica que usa GeoMatrix. El código está en `src/main/kotlin/com/security/modules/licencias`.

## Variables

| Variable | Para qué | Por defecto |
|---|---|---|
| `LICENCIAS_URL` | Dirección del servicio común de licencias | vacía |
| `LICENCIAS_TOKEN` | Token de PLAFT ante el servicio (va como `Authorization: Bearer`) | vacío |
| `LICENCIAS_PRODUCTO` | Producto que se consulta | `plaft` |

Si `LICENCIAS_URL` o `LICENCIAS_TOKEN` están vacías, las licencias están **apagadas**: no se consulta nada, no se bloquea nada y no se cobran tokens. Así funcionan el entorno local y las pruebas. Cada llamada al servicio espera como máximo 3 segundos. El token real solo vive en el servidor y en el gestor de contraseñas.

## De qué empresa es la licencia

La empresa del servicio es el `uuid` de la empresa en PLAFT (tabla `empresas`). Debe ser el mismo UUID de la empresa en la cuenta Sulotec: el administrador de Sulotec puede escribirlo al crear o editar la empresa (`POST /api/empresas` y `PUT /api/empresas/{id}` aceptan `uuid`; si no viene al crear, se genera uno). La cuenta guarda su empresa por nombre, y de ahí se saca el UUID. Una empresa sin UUID no se consulta ni se bloquea.

El administrador de Sulotec (rol `ADMINISTRADOR`) nunca se bloquea. Si tiene empresa, sus consumos se envían marcados como `hecho_por_sulotec`; si no tiene empresa, no se consume nada.

## Las tres acciones que consumen tokens

Cada una consume 1 token, y se pide permiso **antes** de hacer la acción:

| Acción | Tipo | Clave idempotente |
|---|---|---|
| Búsqueda en listas negativas (solo la página 0; pasar de página no consume) | `busqueda_listas` | Misma persona y mismos criterios en una ventana de 2 minutos = misma clave, así un doble clic o recargar no cobra dos veces |
| Evaluación de scoring | `scoring` | Una nueva por evaluación |
| Mensaje a Indira | `mensaje_indira` | Una nueva por mensaje |

Nada más consume tokens (ni los PDF, ni las matrices, ni las denuncias, ni las operaciones, ni la búsqueda de personas del ML). Si la acción falla después de consumir, el consumo se anula.

Si el servicio rechaza el consumo: bolsa agotada → **402** `LICENCIA_BOLSA_AGOTADA`; solo lectura → **403** `LICENCIA_SOLO_LECTURA`; suspendida → **403** `LICENCIA_SUSPENDIDA`; cancelada → **403** `LICENCIA_CANCELADA`.

## Estados y lo que se bloquea

| Estado | Qué pasa con las escrituras (POST, PUT, PATCH, DELETE) |
|---|---|
| `ACTIVA`, sin licencia registrada, desconocido o apagado | Todo pasa |
| `SOLO_LECTURA` | Se bloquea todo (403) salvo desactivar usuarios e invitar usuarios |
| `SUSPENDIDA` o `CANCELADA` | Se bloquea todo (403) salvo desactivar usuarios |

Nunca se bloquean: las lecturas, la sesión (`/api/auth/**`), lo público, `/api/licencia`, `/api/empresas/**`, el canal de denuncias (crear, seguimiento y subir archivos) y los cálculos de matriz que no guardan.

## Cupo de usuarios

Antes de crear una cuenta, reactivar una inactiva, mover una cuenta activa a otra empresa (cupo de la empresa destino) o hacer una carga masiva (todas las cuentas válidas del lote deben caber), PLAFT pregunta el cupo. Sin cupo responde **409** `LICENCIA_SIN_CUPO` con `activos` y `usuarios_max`. El cupo se exige a todos, también al administrador de Sulotec. Después de cada cambio, PLAFT reporta a Sulotec cuántas cuentas activas tiene la empresa (después de guardar y sin demorar la respuesta), y lo hace también una vez al arrancar para todas las empresas activas con UUID. Cuenta activa = cualquier cuenta con `activo = true` en esa empresa, de cualquier rol.

## Si Sulotec no responde

- La licencia se guarda 5 minutos por empresa. Si el servicio falla (red, tiempo agotado, 5xx, 401 o respuesta inválida), se usa la última conocida y se espera 30 segundos antes de volver a preguntar. Si nunca se conoció, se trata como activa. Un 401 queda como error en el log (token mal puesto).
- Los consumos se permiten y quedan en la cola `consumos_licencia_pendientes`, que se reintenta cada minuto con la misma clave (el servicio no descuenta dos veces). Excepción: si la última licencia conocida estaba bloqueada, se responde el 403 o el 402 que corresponda.
- El cupo se permite, salvo que la última licencia conocida diga que no se pueden crear usuarios.

## Lo que ve la web

`GET /api/licencia` (cualquier rol; `?refrescar=1` vuelve a preguntar) responde `exento`, `empresa` y `licencia` (estado, plan, usuarios, vencimiento, periodo, tokens, mensaje y enlace a `https://sulotec.com/mi-licencia`). Con `licencia = null` la web no muestra nada. Los errores 402, 403 y 409 de licencia traen además `code` y `enlace` (y `estado`, o `activos` y `usuarios_max`).

## Tablas

- `consumos_licencia_pendientes`: la cola de reintento (consumos y anulaciones que no llegaron a Sulotec). La crea Hibernate al arrancar.
- `licencias_empresa`, `movimientos_credito`, `tarifas_credito` y `avisos_licencia` eran del módulo propio de créditos que se retiró. Quedan en la base sin uso (Hibernate no borra tablas); se pueden borrar a mano después de respaldar la base.
