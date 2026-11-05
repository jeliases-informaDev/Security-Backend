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

## Las acciones que se avisan a Sulotec

**El costo lo decide Sulotec, se cambia en sulotec.com/licencias.** PLAFT no escribe ningún costo: solo avisa qué acción se hizo (`tipo`) y Sulotec aplica su tarifa para ese tipo (puede ser 0). Por compatibilidad PLAFT sigue mandando `cantidad = 1`, que el servicio ignora. La respuesta permitida trae `cobrado` (lo que se descontó) y a veces `sin_descuento: true`; si un servicio viejo no los manda, no pasa nada.

Se pide permiso **antes** de hacer la acción:

| Acción | Tipo | Dónde | Clave idempotente |
|---|---|---|---|
| Búsqueda en listas negativas (solo la página 0; pasar de página no avisa) | `busqueda_listas` | `ListaNegativaService.buscar` | Misma persona y mismos criterios en una ventana de 2 minutos = misma clave (un doble clic o recargar no cobra dos veces) |
| Descarga del PDF de una coincidencia (`GET /api/listas-negativas/{id}/pdf`) | `lista_pdf` | `ListaNegativaService.exportarPdf` | Misma persona y misma entidad en 2 minutos = misma clave |
| Evaluación de scoring ejecutada y guardada | `scoring` | `ScoringService.evaluar` | Una nueva por evaluación |
| Registrar formalmente una matriz (estado ABIERTO; los borradores no avisan) | `matriz_registrar` | `MatrizRiesgoRegistroService.registrar` | Una nueva por registro |
| Exportar una matriz a PDF | `matriz_pdf` | `MatrizRiesgoPdfController.exportarPdf` | Misma persona y misma matriz en 2 minutos = misma clave |
| Registrar una denuncia (solo si la registra una cuenta con empresa) | `denuncia_crear` | `CrearDenunciaUseCase.ejecutar` | Una nueva por denuncia |
| Subir un lote de archivos a una denuncia (solo si quien sube es una cuenta con empresa) | `denuncia_documentos` | `SubirDocumentosDenunciaUseCase.ejecutar` | Una nueva por lote |
| Insertar una operación | `operacion_registrar` | `OperacionService.registrar` | Una nueva por registro |
| Exportar una operación a PDF | `operacion_pdf` | `OperacionPdfController.exportarPdf` | Misma persona y mismo registro en 2 minutos = misma clave |
| Mensaje a Indira | `mensaje_indira` | `MlService` | Una nueva por mensaje |

Las siete nuevas usan `CobroAcciones` (en `modules/licencias`), con el mismo mecanismo que las tres primeras. Si la acción falla después de avisar, el consumo se anula. Sin licencias, sin sesión o con una cuenta sin empresa con UUID no se avisa nada.

**El canal de denuncias nunca se frena:** Sulotec responde "permitido" para `denuncia_crear` y `denuncia_documentos` aunque no haya saldo, y además PLAFT deja pasar la denuncia aunque Sulotec dijera que no (o aunque la última licencia conocida estuviera bloqueada).

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

`GET /api/licencia` (cualquier rol; `?refrescar=1` vuelve a preguntar) responde `exento`, `empresa` y `licencia` (estado, plan, usuarios, vencimiento, periodo, tokens, `tarifas`, mensaje y enlace a `https://sulotec.com/mi-licencia`). `tarifas` es la lista `[{ tipo, nombre, tokens, bloquea }]` tal cual la manda Sulotec (lista vacía si no la manda). Con `licencia = null` la web no muestra nada. Los errores 402, 403 y 409 de licencia traen además `code` y `enlace` (y `estado`, o `activos` y `usuarios_max`). Los errores salen siempre como JSON, también en las descargas de PDF.

## Tablas

- `consumos_licencia_pendientes`: la cola de reintento (consumos y anulaciones que no llegaron a Sulotec). La crea Hibernate al arrancar.
- `licencias_empresa`, `movimientos_credito`, `tarifas_credito` y `avisos_licencia` eran del módulo propio de créditos que se retiró. Quedan en la base sin uso (Hibernate no borra tablas); se pueden borrar a mano después de respaldar la base.
