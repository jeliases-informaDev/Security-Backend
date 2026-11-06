# Registro de Operaciones (diseño nuevo, entregas A y B)

Qué hace el módulo, con qué reglas, y qué queda para la entrega B. El contrato completo con ejemplos JSON lo tiene quien hace
las pantallas (`CONTRATO-OPERACIONES.md`); aquí va lo esencial. Reemplaza a `REGISTRO-OPERACION.md`, que describe el modelo
anterior (números `OP-…` puestos por el navegador).

## Qué hace

Es la bitácora de las operaciones de cada empresa cliente (Res. SBS 789-2018, según el diseño). En esta entrega:

| Pantalla | Ruta (todas bajo `/api/operaciones`) | Tokens |
|---|---|---|
| Lista, buscador y filtros (RO 0, RO 1, RO 11) | `GET /resumen` | — |
| Indicadores de la lista (RO 1) | `GET /indicadores?periodo=AAAA-MM` | — |
| Catálogos del formulario | `GET /catalogos` | — |
| Alta (RO 2, RO 3) y «Registro guardado» (RO 4) | `POST /insertar-operacion` (201) | `operacion_registrar` |
| Detalle (RO 5) | `GET /{id}` | — |
| Editar con motivo (RO 6) | `PUT /{id}` | — |
| Historial de cambios (RO 7) | `GET /{id}/historial?tipo=TODO\|EDICION\|RESPONSABLE\|DESCARGA\|ANALISIS` | — |
| Cambiar responsable (RO 8) | `GET /{id}/responsables`, `PUT /{id}/responsable` | — |
| PDF con vista previa (RO 9, RO 10) | `GET /{id}/exportar-pdf` | `operacion_pdf` |

Siguen igual: `GET /obtener-usuario`, `GET /usuarios-por-empresa` (ahora admite `soloActivos=true`), `PATCH /{id}/usuario`
(cambia la **cuenta dueña**, solo para el administrador; no es el responsable) y las rutas de `/catalogos/*`.

## Reglas

- **Cada empresa ve solo lo suyo.** La empresa sale de la cuenta autenticada; la de un registro es la de su cuenta dueña (si ya no
  existe, la guardada). Lo ajeno responde `404 OPERACIONES_NO_ENCONTRADA`, igual que lo inexistente, en todas las rutas por id. La
  lista, el buscador, los filtros y los indicadores se resuelven en la base siempre dentro de esa empresa. El administrador
  universal de Sulotec (rol `ADMINISTRADOR`) ve todo y puede pasar `empresa`.
- **Número `RO-AAAA-NNNNNN`:** lo pone el servidor, correlativo por empresa y año (hora de Lima). Fila de contador
  `contador_registro_operaciones` bloqueada con `SELECT … FOR UPDATE` dentro de la transacción del alta, más el índice único
  `(empresa, numero_registro)`: dos altas a la vez no repiten número. Si el alta falla, el número no se consume. Los `OP-…` conservan
  su número.
- **Fecha y hora de registro:** las pone el servidor (Lima) y no se editan. La fecha de la operación sí, y puede ser anterior a hoy
  (la pantalla solo recuerda que se registra el mismo día).
- **Datos mínimos para guardar** (los 5 de RO 2): persona que realiza (tipo y N.º de documento, nombres, apellidos; con
  «SIN DOCUMENTO», el motivo en lugar del número); operación (tipo, fecha, monto > 0 con hasta 2 decimales); modalidad y moneda;
  origen de los fondos; al menos un beneficiario (nombre, tipo y N.º de documento). Además la oficina. «Otro (especificar)» pide su
  texto. Persona representada jurídica: razón social obligatoria. Responde `400 OPERACIONES_DATOS_INVALIDOS` con `campos`
  (campo → mensaje).
- **Responsable:** al crear, quien registra. Se cambia solo a un usuario activo de la empresa del registro. No se puede dejar vacío.
- **Editar:** motivo obligatorio (hasta 300). Se reemplaza el formulario completo; el servidor calcula los cambios comparando lo
  legible antes y después y los guarda en el historial con su etiqueta. Sin cambios → `400 OPERACIONES_SIN_CAMBIOS`. En un registro
  anterior al diseño nuevo, **un valor que no cambia se acepta tal cual** (tipo escrito a mano, sin origen de fondos…). No se borra
  ningún registro.
- **Historial solo de inserción** (`historial_operaciones` + `historial_operaciones_cambios`): creación, edición (motivo y
  «antes → después» por campo), cambio de responsable y descarga del PDF (con su código y su huella). Guarda la cuenta y su nombre
  en ese momento. No hay rutas ni métodos para editar ni borrar eventos. Un registro sin evento de creación es anterior al
  historial y se muestra con un evento «Registro anterior al historial» (no se guarda: se arma al leer).
- **Conservación:** `conservarHasta` = fecha de la operación + 5 años (dato que muestra la pantalla; no borra nada).
- **PDF:** una página (si no cabe, se reduce entero). Huella = SHA-256 del contenido completo del registro (mismo contenido, misma
  huella; si se edita, cambia). Código de verificación = 8 caracteres de un SHA-256 de la huella, la fecha-hora, la cuenta y el
  registro (propio de cada PDF). Cabeceras `X-Codigo-Verificacion`, `X-Huella-Sha256` y `X-Paginas`. La vista previa y la descarga
  usan el mismo archivo (una sola llamada, un solo cobro). Cada PDF se anota en el historial dentro de la misma acción: si no se
  pudiera anotar, el cobro se anula.
- **Licencia en solo lectura / suspendida / cancelada:** bloquea `POST /insertar-operacion`, `PUT /{id}`, `PUT /{id}/responsable` y
  `PATCH /{id}/usuario` (403). Las lecturas siguen; el PDF depende de la respuesta de Sulotec al cobro.
- **Catálogos como datos:** `catalogo_operaciones`, sembrado al arrancar desde `src/main/resources/operaciones/catalogos-registro.csv`
  (solo agrega lo que falta; no cambia ni borra). Trae las opciones exactas de las fichas «para desarrollo»: tipo de documento (5),
  estado civil (5), modalidad de pago (9), origen de los fondos (7) y tipos de operación del sector inmobiliario (12). El front manda
  el código; los registros anteriores guardaron texto o un id y se muestran con su nombre (`…Nombre`).

## Base de datos

Sin pasos a mano. Hibernate (`ddl-auto: update`) agrega las columnas nuevas (todas opcionales: los registros anteriores no las
tienen) y las tablas nuevas. `EsquemaOperacionesInicializador`, al arrancar y mirando antes `information_schema`:

1. quita el `NOT NULL` de `beneficiarios_operacion_v2.fecha_nacimiento`;
2. crea el índice único `uk_reg_v2_empresa_numero (empresa, numero_registro)` y, después, quita el único viejo de `numero_registro`.

Columnas nuevas: `personas_operacion` (`motivo_sin_documento`, `estado_civil`, `tipo_persona`, `razon_social`, `representante_legal`,
`representante_tipo_documento`, `representante_numero_documento`, `accionistas_mayores_25`, `domicilio_fiscal`) y `operaciones_v2`
(`modalidad_pago_texto`, `origen_fondos`, `origen_fondos_texto`, `numero_comprobante`, `descripcion`). Tablas nuevas:
`contador_registro_operaciones`, `catalogo_operaciones`, `historial_operaciones`, `historial_operaciones_cambios`.

## Pruebas

`src/test/kotlin/com/security/modules/operaciones/`: validación y datos mínimos, cambios y huella, historial, alta, edición (también
de un registro anterior), responsable, lista e indicadores, **acceso cruzado en todas las rutas por id** y en lista/indicadores, PDF,
y `RegistroOperacionesMysqlTest`, que corre contra la MySQL del CI: lista, filtros e indicadores separados por empresa, numerador con
altas simultáneas, índice único por empresa, esquema e historial.

## Entrega B

Contrato completo: `CONTRATO-OPERACIONES-B.md` (lo tiene quien hace las pantallas). Ninguna ruta de la B consume tokens.

| Pantalla | Ruta (bajo `/api/operaciones`) | Quién |
|---|---|---|
| Sector, umbrales y oficinas (RO 16) | `GET`/`PUT /configuracion` | ver: todos · guardar: administrador |
| Tipo de cambio mensual | `GET`/`PUT /tipos-cambio` | ver: todos · cargar: administrador |
| Verificación de umbral (RO 2–5) | `POST /verificar-umbral` (y al registrar y editar) | todos |
| Marcar como inusual (RO 12) | `GET`/`PUT /{id}/inusualidad`, `POST /{id}/inusualidad/adjuntos`, `GET …/adjuntos/{adjuntoId}` | todos |
| Calificar | `POST /{id}/inusualidad/decision` | administrador |
| Preparar el ROS (RO 13) | `GET`/`PUT /{id}/ros` | ver: todos · registrar: administrador |
| Exportar (RO 14, RO 15) | `GET /exportaciones/validacion`, `POST /exportaciones`, `GET /exportaciones`, `GET /exportaciones/{id}`, `GET /exportaciones/{id}/archivo?tipo=xlsx\|csv`, `POST /exportaciones/{id}/constancia`, `GET /exportaciones/{id}/constancia/archivo` | ver: todos · generar y constancia: administrador |

«Administrador» = el administrador de la empresa (`ADMINISTRADOR_EMPRESA`) o el universal de Sulotec, hasta que exista el rol de
oficial de cumplimiento; si no, 403 `OPERACIONES_SOLO_ADMINISTRADOR`.

Reglas de la B:

- **Sector y umbrales:** los sectores son los 12 de la ficha de RO 16. Solo «Construcción y/o inmobiliaria» tiene umbrales cargados
  (`umbral_operaciones`, sembrado desde `operaciones/umbrales-norma.csv`: lotes de terreno US$ 30 000; vivienda, oficinas, uso
  comercial, uso público y estacionamientos US$ 100 000, con los nombres del diseño). Los demás sectores quedan «Por configurar»: no
  se inventan umbrales. El tipo de bien sale del tipo de operación (columna `tipo_bien` del catálogo). El umbral interno es opcional,
  menor que el mayor de la norma y con motivo. Las oficinas configuradas pasan a ser la lista del formulario.
- **Tipo de cambio:** soles por unidad, promedio de venta del mes, cargado a mano (el de la empresa manda sobre el de Sulotec). Una
  operación del mes M usa el del mes M−1. Si falta, la verificación dice «Falta el tipo de cambio de <mes>» y **no bloquea** el
  registro. PLAFT no lo trae de ningún lado.
- **Verificación de umbral:** se calcula y se guarda con el registro (estado, monto en US$, tipos de cambio usados, umbral, tipo de
  bien, sector) al registrar y al editar; evento `UMBRAL` en el historial (del sistema). Es informativa.
- **Inusual y ROS:** un análisis por registro: `EN_ANALISIS` → `DESCARTADA` o `SOSPECHOSA` (una sola decisión, con el «Análisis y
  evaluación» escrito). Sospechosa abre un plazo de 24 horas para registrar el número de ROS (una sola vez). El envío del ROS es en
  ROSEL, fuera de PLAFT. Adjuntos PDF, DOC o DOCX hasta 10 MB en el almacenamiento de las denuncias; no se borran. Todo va al
  historial como `ANALISIS`.
- **Estado del registro:** `INUSUAL` (en análisis o sospechosa), si no `ENVIADO` (con constancia), si no `PENDIENTE`. Lista,
  pestañas, filtros (estado, oficina, monto en US$) e indicadores (enviados, inusuales en análisis, sospechosas) lo usan.
- **Exportación:** formato interno «RO a disposición de la UIF» (Excel con hoja «Léeme» y CSV UTF-8 con `;`), una fila por operación
  del periodo (mes de la fecha de la operación) pendiente de envío. **No es una plantilla oficial de la SBS**: la SBS no publica una
  para este sector. Los cuatro formatos de la SBS quedan «Pronto». La validación lista lo que falta por registro (se puede generar
  igual). Los archivos se guardan al generarlos. La constancia (fecha, N.º de acuse, notas, adjunto opcional) pasa a `ENVIADO` los
  registros de esa exportación y anota `ENVIO` en su historial; una sola vez.

Tablas nuevas de la B: `umbral_operaciones`, `configuracion_operaciones` (+ `_oficinas`), `tipo_cambio_operaciones`,
`analisis_operaciones`, `analisis_operaciones_adjuntos`, `exportacion_operaciones` (+ `_registros`). Columnas nuevas:
`operaciones_v2` (`umbral_estado`, `umbral_usd`, `umbral_norma_usd`, `umbral_interno`, `tipo_bien`, `sector`, `monto_usd`, `tc_mes`,
`tc_usd`, `tc_moneda`, `umbral_verificado_en`), `registro_operaciones_v2` (`id_exportacion_envio`, `fecha_envio`) y
`catalogo_operaciones.tipo_bien`. Todo opcional; lo crea Hibernate.

Sigue «Pronto»: los formatos oficiales de la SBS, la carga automática del tipo de cambio, más de un RO por actividad, umbrales y tipos
de operación de los otros sectores, la plantilla ROSEL y el rol de oficial de cumplimiento. Conservación: se muestra 5 años (el
diseño); el reglamento habla de 10 y la norma sectorial de 5: está por confirmar con la UIF.
