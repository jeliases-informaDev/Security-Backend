# Registro de Operaciones (diseño nuevo, entrega A)

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

## Queda para la entrega B (no hay rutas ni tablas a medio hacer)

Sector y umbrales con verificación (RO 16 y la tarjeta «Umbral» de RO 2–5), tipo de cambio, filtro de monto en US$, oficinas como
lista de la empresa, marcar como inusual y análisis (RO 12; pestaña «Análisis» del historial), preparar el ROS (RO 13). La
exportación a la UIF (RO 14, RO 15) y el estado «Enviado» esperan el formato oficial. Mientras tanto todo registro está «Pendiente».
