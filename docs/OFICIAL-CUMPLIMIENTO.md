# Oficial de Cumplimiento (módulo de PLAFT)

Registra al oficial de cumplimiento de cada empresa cliente y todo su ciclo según la Res. SBS 789-2018 (arts. 5 a 13):
designación con un asistente de cuatro pasos (datos de la persona, designación y acta, declaraciones juradas, revisión),
comunicación a la UIF-Perú (que se hace fuera, en SISDEL, y aquí se **registra**), ficha vigente, cambio de datos, pendientes
con la UIF, remoción, cargo vacante e historial que no se puede editar ni borrar.

Código: `src/main/kotlin/com/security/modules/oficial` (`dominio`, `persistencia`, `aplicacion`, `web`).
Pruebas: `src/test/kotlin/com/security/modules/oficial` (la de integración corre contra la MySQL del CI).

## En corto

- **Multiempresa.** La empresa sale de la cuenta autenticada; solo el administrador universal (rol `ADMINISTRADOR`) puede pasar
  `?empresa=<uuid o nombre>`. Lo de otra empresa responde 404 `OC_NO_ENCONTRADO`.
- **Permisos.** Escriben `ADMINISTRADOR` y `ADMINISTRADOR_EMPRESA`; los demás roles solo leen y no descargan las declaraciones
  firmadas. Feriados y reglas: solo el administrador universal.
- **Historial solo de inserción.** Tablas `oc_evento` y `oc_evento_cambio` con `@Immutable`; el repositorio solo guarda y lee.
- **Datos personales** del oficial (documento, correo, teléfono) nunca van a los logs ni a los mensajes de error.
- **Códigos secretos de la UIF** cifrados con AES-256-GCM; ningún endpoint los devuelve (solo «reservados» y la fecha).
- **Plazos** en días hábiles (lunes a viernes menos la tabla `oc_feriado`), calculados por el servidor. Las reglas viven en
  `oc_regla` (datos, no código).
- **Adjuntos**: PDF, DOC y DOCX hasta 10 MB, validados por extensión y contenido; mismo almacenamiento que las denuncias
  (`STORAGE_PROVIDER`), carpeta `oficial/<uuid de la empresa>/`, nombre generado.
- **Licencias**: no consume tokens; las escrituras se bloquean con la licencia en solo lectura, suspendida o cancelada.

## Tablas nuevas (se crean solas con `ddl-auto: update`)

`oc_designacion`, `oc_adjunto`, `oc_remocion`, `oc_pendiente`, `oc_evento`, `oc_evento_cambio`, `oc_regla` (se siembra al
arrancar con los valores por defecto; nunca pisa un valor cambiado) y `oc_feriado` (arranca **vacía**).

## Variables de entorno nuevas

| Variable | Para qué | Cómo generarla |
|---|---|---|
| `OC_CLAVE_CIFRADO` | Clave AES-256 para cifrar los códigos secretos de la UIF. Sin ella (o si no es válida) el módulo funciona, pero rechaza guardar códigos con 409 `OC_CIFRADO_NO_CONFIGURADO`. | `openssl rand -base64 32` (32 bytes en base64; también acepta 64 caracteres hexadecimales). Va solo en el `.env` del servidor. **Si se pierde o se cambia, los códigos ya guardados no se pueden leer** (hoy ninguna pantalla los lee; habría que volver a escribirlos). |

## Feriados

La tabla `oc_feriado` arranca vacía: no se siembran feriados que no se hayan confirmado en una fuente oficial (El Peruano o
gob.pe). Mientras esté vacía, cada plazo trae `cuentaFeriados: false` y la pantalla muestra «Plazo estimado: no cuenta
feriados». El administrador universal los agrega con `POST /api/oficial-cumplimiento/feriados` (fecha, nombre y fuente).

---

# Contrato con el front (versión del 2026-11-06)

Rama del backend: `oficial-cumplimiento` (repo `jeliases-informaDev/Security-Backend`). Este contrato manda sobre la §6 de
`ESPECIFICACION-OFICIAL.md` y sigue `DECISIONES-OC.md`. Cubre **todas las fases** (OC 0 y OC 2 a OC 13).

Todas las rutas piden sesión (`Authorization: Bearer …`). JSON en camelCase. Fechas `AAAA-MM-DD`; fecha-hora
`AAAA-MM-DDTHH:MM:SS` **en hora de Lima, sin zona** (igual que Operaciones y Listas). Los nombres, documentos y correos de los
ejemplos son **inventados**. Este módulo **no consume tokens**.

Base: `/api/oficial-cumplimiento`.

---

## 0. Reglas comunes

### Empresa
- La empresa sale de la cuenta autenticada (`usuarios.empresa` → tabla `empresas`). Nunca del cuerpo ni de la URL.
- Solo el **administrador universal** (rol `ADMINISTRADOR`) puede pasar `?empresa=<uuid o nombre de la empresa>` en **cualquier**
  ruta (lecturas y escrituras). Sin `empresa`, usa la suya; si no tiene → 400 `OC_EMPRESA_REQUERIDA`.
- Todo lo de otra empresa (designación, adjunto, pendiente) responde **404 `OC_NO_ENCONTRADO`**, igual que si no existiera.

### Permisos (DECISIONES §2.1)
| Rol (del token) | Puede |
|---|---|
| `ADMINISTRADOR` (universal de Sulotec) | Todo, en cualquier empresa (con `?empresa=`). Además mantiene feriados y reglas. |
| `ADMINISTRADOR_EMPRESA` | Todo en su empresa: designar, corregir, comunicar, cambiar datos, remover, registrar comunicaciones, subir y borrar adjuntos. |
| `SUPERVISOR`, `USUARIO` y cualquier otro | Solo lectura: estado, ficha, historial, pendientes, catálogos, plazos. **No** descargan los adjuntos de declaraciones (`DJ_REQUISITOS_FIRMADA`, `DJ_DESIGNANTE_FIRMADA`): en sus respuestas esos adjuntos llegan `null`. |

Una escritura sin permiso → **403 `OC_SIN_PERMISO`**. Cada respuesta de ficha/estado trae `permisos` para pintar o esconder botones.

### Licencia
Sin trato especial: el guardia de licencias común bloquea con 403 (`LICENCIA_SOLO_LECTURA` / `LICENCIA_SUSPENDIDA` /
`LICENCIA_CANCELADA`) todo `POST`/`PUT`/`PATCH`/`DELETE` de este módulo cuando la licencia no permite escribir, **incluida** la
vista previa de la declaración (es `POST` porque lleva datos personales: no van en la URL). Las lecturas siguen.

### Errores
Formato común del proyecto; `campos` solo aparece en errores de datos. Los mensajes **nunca** repiten documento, correo, teléfono
ni códigos.

```json
{ "timestamp": "2026-11-06T10:42:00.123-05:00", "status": 400, "error": "Bad Request",
  "message": "Escribe los nombres.", "path": "/api/oficial-cumplimiento/designaciones",
  "code": "OC_DATOS_INVALIDOS",
  "campos": { "persona.nombres": "Escribe los nombres.", "persona.correo": "El correo no tiene un formato válido." } }
```

| Código | HTTP | Cuándo |
|---|---|---|
| `OC_DATOS_INVALIDOS` | 400 | Falta un dato o no vale (código fuera de catálogo, fecha futura, documento con formato errado, declaración incompleta, adjunto de otro ámbito…). `campos` trae cada campo con su mensaje (claves como `persona.nombres`, `designacion.adjuntoId`, `declaraciones.requisitos.puntos`, `fechaNotificacion`); `message` es el primero. |
| `OC_SIN_CAMBIOS` | 400 | Cambiar datos o corregir sin cambiar nada. |
| `OC_FUNCION_PRONTO` | 400 | Algo marcado «Pronto»: cargo `ALTERNO` o `CORPORATIVO`, motivo `AUSENCIA_TEMPORAL`, PDF de la declaración del designante. |
| `OC_ADJUNTO_INVALIDO` | 400 | Archivo vacío, de más de 10 MB, extensión distinta de PDF/DOC/DOCX, o contenido que no corresponde a la extensión. |
| `OC_EMPRESA_REQUERIDA` | 400 | Administrador universal sin empresa propia y sin `?empresa=`. |
| `OC_SIN_EMPRESA` | 403 | La cuenta no tiene empresa (o su empresa no está en la lista de empresas). |
| `OC_SIN_PERMISO` | 403 | El rol no puede hacer esa acción, o pide un adjunto de declaración sin ser administrador. |
| `OC_NO_ENCONTRADO` | 404 | No existe **o es de otra empresa**. |
| `OC_TITULAR_ACTIVO` | 409 | Designar un titular cuando ya hay uno pendiente, en evaluación o vigente. |
| `OC_ESTADO_INVALIDO` | 409 | La acción no cabe en el estado actual (p. ej. remover una designación pendiente, corregir una vigente, «No procedente» sobre una vigente). |
| `OC_CIFRADO_NO_CONFIGURADO` | 409 | Se mandó un código secreto y el servidor no tiene `OC_CLAVE_CIFRADO`. No se guarda nada de esa llamada. |
| `OC_ADJUNTO_EN_USO` | 409 | Borrar un adjunto ya ligado a una designación, remoción o comunicación, o ligar uno que ya está ligado a otra. |
| `OC_YA_COMUNICADO` | 409 | Registrar la comunicación de un pendiente que ya se comunicó. |
| `OC_FERIADO_DUPLICADO` | 409 | Agregar un feriado con una fecha que ya está. |
| `LICENCIA_*` | 402/403 | Manejador global de licencias. |

Un cuerpo JSON mal formado sigue respondiendo el 400 genérico de siempre (sin `code`).

### Objetos comunes

**`Plazo`** — lo calcula siempre el servidor; el front no suma fechas.
```json
{ "regla": "PLAZO_COMUNICAR_DESIGNACION", "unidad": "DIAS_HABILES", "total": 15,
  "desde": "2026-11-03", "vence": "2026-11-24",
  "diasRestantes": 14, "avance": 7,
  "estado": "EN_PLAZO", "estadoEtiqueta": "En plazo",
  "cuentaFeriados": false }
```
- `unidad`: `DIAS_HABILES` (lunes a viernes menos la tabla de feriados) o `DIAS` (corridos).
- Días hábiles: se cuentan **desde el día siguiente** a `desde` (así salen las fechas del diseño: 03/11 + 15 = 24/11).
- `diasRestantes`: días (hábiles o corridos, según `unidad`) **después de hoy y hasta `vence` inclusive**; 0 si vence hoy o ya venció.
- `avance`: entero 0–100 para la barra (`(total − diasRestantes) / total`).
- `estado`: `EN_PLAZO` · `POR_VENCER` (quedan ≤ `UMBRAL_POR_VENCER` = 3 días hábiles, incluido el día del vencimiento) · `VENCIDO` (hoy es posterior a `vence`).
  Etiquetas: «En plazo», «Por vencer», «Vencido». Tonos sugeridos: verde, ámbar, rojo.
- `cuentaFeriados`: `false` mientras la tabla de feriados esté vacía → mostrar «Plazo estimado: no cuenta feriados».
  Hoy la tabla **está vacía** (no se pudo confirmar el calendario 2026 en una fuente oficial; ver §12).

**`Adjunto`**
```json
{ "id": "6f1c…uuid", "ambito": "ACTA_DESIGNACION", "nombre": "acta-prueba.pdf",
  "tipoContenido": "application/pdf", "tamanoBytes": 421888, "subidoEn": "2026-11-03T10:40:12" }
```

**`Etiqueta`** — `{ "codigo": "VIGENTE", "texto": "Vigente", "tono": "verde" }`. Tonos: `verde`, `ambar`, `rojo`, `lila`, `gris`.

**`Permisos`** — `{ "puedeDesignar": true, "puedeCorregir": false, "puedeComunicar": false, "puedeCambiarDatos": true, "puedeRemover": true, "puedeRegistrarPendientes": true }`
(ya combina el rol y el estado: si es `false`, el botón va deshabilitado).

**`Usuario`** (quién hizo algo) — `{ "usuario": "admin.prueba", "nombreCompleto": "Ana Prueba Torres" }` (guardado en ese momento).

---

## 1. Enumeraciones (códigos que manda y recibe el front)

| Catálogo | Códigos (orden) y nombre |
|---|---|
| `tiposCargo` | `TITULAR` «Titular» · `ALTERNO` «Alterno» (Pronto) · `CORPORATIVO` «Corporativo» (Pronto) |
| `dedicaciones` | `EXCLUSIVA` «Exclusiva» · `NO_EXCLUSIVA` «No exclusiva» |
| `tiposEmpresa` | `PERSONA_NATURAL` «Persona natural» · `PERSONA_JURIDICA` «Persona jurídica» · `SIN_DIRECTORIO` «Sin directorio» |
| `tiposDocumento` | `DNI` «DNI» · `CE` «CE (carné de extranjería)» · `PASAPORTE` «PASAPORTE» (inicial `DNI`) |
| `vinculos` | `LABORAL` «Laboral (planilla)» · `CONTRACTUAL_DIRECTO` «Contractual directo» |
| `quienDesigna` | `SUJETO_OBLIGADO` «El propio sujeto obligado (persona natural)» · `DIRECTORIO` «Directorio u órgano equivalente» · `GERENTE_GENERAL` «Gerente general» · `GERENTE` «Gerente» · `TITULAR_GERENTE` «Titular-gerente» · `ADMINISTRADOR` «Administrador» |
| `documentosDesignacion` | `ACTA_DIRECTORIO` «Acta de sesión de directorio» · `ACTA_JUNTA` «Acta de junta general de accionistas» · `OTRO` «Otro documento que acredite la designación» |
| `resultadosUif` | `EN_EVALUACION` «En evaluación» · `PROCEDENTE` «Procedente» · `NO_PROCEDENTE` «No procedente» |
| `motivosRemocion` | `REMOCION_EMPRESA` · `INCUMPLE_REQUISITO` · `VACANCIA` · `AUSENCIA_TEMPORAL` (Pronto); nombres y ayudas exactos del diseño (OC 12) |
| `ambitosAdjunto` | `ACTA_DESIGNACION` · `HOJA_DE_VIDA` · `DOCUMENTO_VINCULO` · `DJ_REQUISITOS_FIRMADA` · `DJ_DESIGNANTE_FIRMADA` · `CONSTANCIA_UIF` · `ACTA_REMOCION` · `CONSTANCIA_COMUNICACION` |
| Estado de la **empresa** | `SIN_OFICIAL` · `PENDIENTE_COMUNICAR` · `EN_EVALUACION` · `VIGENTE` · `VACANTE` |
| Estado de una **designación** | `PENDIENTE_COMUNICAR` · `EN_EVALUACION` · `VIGENTE` · `REMOVIDA` |
| Tipo de **pendiente** | `CAMBIO_DATOS` · `REMOCION` |
| Tipo de **evento** | `DESIGNACION_GUARDADA` · `DESIGNACION_CORREGIDA` · `COMUNICACION_PRESENTADA` · `DESIGNACION_PROCEDENTE` · `DESIGNACION_NO_PROCEDENTE` · `CODIGOS_ACTUALIZADOS` · `CAMBIO_DATOS` · `CAMBIO_COMUNICADO` · `REMOCION` · `REMOCION_COMUNICADA` |

Quién puede designar según el tipo de empresa (art. 7.1, el servidor lo valida):
`PERSONA_NATURAL` → `SUJETO_OBLIGADO`; `PERSONA_JURIDICA` → `DIRECTORIO`; `SIN_DIRECTORIO` → `GERENTE_GENERAL`, `GERENTE`,
`TITULAR_GERENTE`, `ADMINISTRADOR`.

---

## 2. `GET /catalogos` — todo lo de los desplegables y las reglas

```json
{
  "tiposCargo": [
    { "codigo": "TITULAR", "nombre": "Titular", "disponible": true },
    { "codigo": "ALTERNO", "nombre": "Alterno", "disponible": false },
    { "codigo": "CORPORATIVO", "nombre": "Corporativo", "disponible": false } ],
  "dedicaciones": [ { "codigo": "EXCLUSIVA", "nombre": "Exclusiva" }, { "codigo": "NO_EXCLUSIVA", "nombre": "No exclusiva" } ],
  "tiposEmpresa": [
    { "codigo": "PERSONA_NATURAL", "nombre": "Persona natural", "quienDesigna": "El propio dueño. Puede ser su propio oficial.",
      "designantesPermitidos": ["SUJETO_OBLIGADO"] },
    { "codigo": "PERSONA_JURIDICA", "nombre": "Persona jurídica", "quienDesigna": "El directorio u órgano equivalente.",
      "designantesPermitidos": ["DIRECTORIO"] },
    { "codigo": "SIN_DIRECTORIO", "nombre": "Sin directorio",
      "quienDesigna": "El gerente general, gerente, titular-gerente o administrador.",
      "designantesPermitidos": ["GERENTE_GENERAL", "GERENTE", "TITULAR_GERENTE", "ADMINISTRADOR"] } ],
  "nacionalidades": [ { "codigo": "PE", "nombre": "Perú" }, { "codigo": "AF", "nombre": "Afganistán" } ],
  "nacionalidadInicial": "PE",
  "tiposDocumento": [ { "codigo": "DNI", "nombre": "DNI" }, { "codigo": "CE", "nombre": "CE (carné de extranjería)" },
                      { "codigo": "PASAPORTE", "nombre": "PASAPORTE" } ],
  "tipoDocumentoInicial": "DNI",
  "vinculos": [ { "codigo": "LABORAL", "nombre": "Laboral (planilla)" }, { "codigo": "CONTRACTUAL_DIRECTO", "nombre": "Contractual directo" } ],
  "quienDesigna": [ { "codigo": "SUJETO_OBLIGADO", "nombre": "El propio sujeto obligado (persona natural)" } ],
  "documentosDesignacion": [ { "codigo": "ACTA_DIRECTORIO", "nombre": "Acta de sesión de directorio" } ],
  "resultadosUif": [ { "codigo": "EN_EVALUACION", "nombre": "En evaluación" }, { "codigo": "PROCEDENTE", "nombre": "Procedente" },
                     { "codigo": "NO_PROCEDENTE", "nombre": "No procedente" } ],
  "motivosRemocion": [
    { "codigo": "REMOCION_EMPRESA", "nombre": "Remoción por decisión de la empresa",
      "ayuda": "Exige sustento y la aprobación de quien designó al oficial.", "disponible": true },
    { "codigo": "INCUMPLE_REQUISITO", "nombre": "El oficial dejó de cumplir un requisito",
      "ayuda": "El oficial debe avisarlo por escrito en 5 días hábiles; la empresa lo remueve y lo informa a la UIF.", "disponible": true },
    { "codigo": "VACANCIA", "nombre": "Vacancia del cargo",
      "ayuda": "Renuncia, fallecimiento u otra causa. No puede durar más de 30 días.", "disponible": true },
    { "codigo": "AUSENCIA_TEMPORAL", "nombre": "Ausencia temporal",
      "ayuda": "Se activa al alterno hasta por 4 meses, avisando 5 días hábiles antes.", "disponible": false } ],
  "ambitosAdjunto": [ { "codigo": "ACTA_DESIGNACION", "nombre": "Acta o documento de designación" } ],
  "reglas": [ { "codigo": "PLAZO_COMUNICAR_DESIGNACION", "valor": 15, "unidad": "DIAS_HABILES",
                "articulo": "Res. SBS 789-2018, art. 7.2", "descripcion": "Comunicar la designación a la UIF" } ],
  "declaracionRequisitos": [ { "letra": "a", "texto": "Tengo experiencia en las actividades de la empresa, en la lucha contra el LA/FT o como oficial de cumplimiento." } ],
  "declaracionRequisitosPuntoH": {
    "LABORAL": "Tengo vínculo laboral con la empresa y actuaré con autonomía e independencia.",
    "CONTRACTUAL_DIRECTO": "Tengo vínculo contractual directo con la empresa y actuaré con autonomía e independencia." },
  "declaracionDesignante": { "texto": "Declaro que la designación fue efectuada por la persona u órgano indicado y que adjunto copia del documento que la acredita." },
  "obligaciones": [ { "nombre": "Informe anual del oficial (IAOC)", "frecuencia": "CADA AÑO" } ],
  "adjuntos": { "extensiones": ["pdf", "doc", "docx"], "tamanoMaximoBytes": 10485760 },
  "feriados": { "cargados": 0, "cuentaFeriados": false },
  "pronto": { "guiaRequisitos": true, "designarAlterno": true, "ausenciaTemporal": true, "corporativo": true,
              "pdfDeclaracionDesignante": true, "avisos": true }
}
```
- Aquí se recortan las listas; llegan completas. `nacionalidades` = los 250 países ISO 3166 (alfa-2 + nombre del país en español,
  Perú primero), del mismo catálogo que usa Scoring.
- `declaracionRequisitos` trae las 8 letras `a`–`h` con el texto del diseño (la `h` en su versión `LABORAL`); para el punto `h`
  usa `declaracionRequisitosPuntoH[vinculo]` según el vínculo elegido en el paso 1.
- `obligaciones`: las cinco del diseño (OC 9), tal cual: IAOC «CADA AÑO» · Verificar listas y congelamientos «PERMANENTE» ·
  Comunicar ROS a la UIF «EN 24 HORAS» · Capacitar al personal «CADA AÑO» · Custodia de documentos «5 AÑOS».
- `reglas` completas: ver §11.

---

## 3. `GET /estado` — decide la pantalla raíz (OC 0, OC 7, OC 9, OC 13)

```json
{
  "empresa": { "uuid": "0b6e…", "nombre": "Empresa Ficticia S.A.C." },
  "estado": "PENDIENTE_COMUNICAR",
  "estadoEtiqueta": "Pendiente de comunicar",
  "tipoEmpresa": "PERSONA_JURIDICA",
  "titular": {
    "id": "c2a1…uuid", "estado": "PENDIENTE_COMUNICAR", "estadoEtiqueta": "Pendiente de comunicar",
    "nombreCompleto": "Ana Prueba Torres", "iniciales": "AP",
    "tipoCargo": "TITULAR", "tipoCargoNombre": "Titular", "dedicacion": "NO_EXCLUSIVA", "dedicacionNombre": "No exclusiva",
    "documento": "DNI 00000001", "fechaDesignacion": "2026-11-03", "guardadaEn": "2026-11-03T10:42:00",
    "designaNombre": "Directorio u órgano equivalente", "nombreOrgano": "Directorio de Empresa Ficticia S.A.C.",
    "resultadoUif": null, "fechaNotificacion": null },
  "plazoComunicacion": { "regla": "PLAZO_COMUNICAR_DESIGNACION", "unidad": "DIAS_HABILES", "total": 15, "desde": "2026-11-03",
    "vence": "2026-11-24", "diasRestantes": 14, "avance": 7, "estado": "EN_PLAZO", "estadoEtiqueta": "En plazo", "cuentaFeriados": false },
  "observacionUif": null,
  "vacancia": null,
  "pendientes": [],
  "permisos": { "puedeDesignar": false, "puedeCorregir": true, "puedeComunicar": true, "puedeCambiarDatos": false,
                "puedeRemover": false, "puedeRegistrarPendientes": true }
}
```

| `estado` | Pantalla | Qué trae |
|---|---|---|
| `SIN_OFICIAL` | OC 0 | `titular: null`, todo lo demás `null`/vacío. |
| `PENDIENTE_COMUNICAR` | OC 7 | `titular` y `plazoComunicacion` (15 días hábiles desde la fecha de designación). Si la UIF respondió «No procedente», `plazoComunicacion: null` y `observacionUif: { "motivo": "…", "fechaNotificacion": "2026-11-12" }` (la norma no fija un plazo nuevo para volver a presentar). |
| `EN_EVALUACION` | OC 9 (ficha) con etiqueta «En evaluación» y botón «Registrar respuesta» (= OC 8). La designación **sigue en el cargo** mientras tanto. | `titular`, `plazoComunicacion: null` (ya se presentó). |
| `VIGENTE` | OC 9 | `titular`. |
| `VACANTE` | OC 13 | `titular: null` y `vacancia` (abajo). |

`tipoEmpresa`: el guardado en la última designación de la empresa (para precargar el asistente y pintar «Quién designa en tu
empresa»); `null` si nunca se designó.

`vacancia` (solo con `VACANTE`):
```json
{ "designacionRemovidaId": "c2a1…uuid", "nombreCompleto": "Ana Prueba Torres",
  "motivo": "REMOCION_EMPRESA", "motivoNombre": "Remoción por decisión de la empresa",
  "fechaDecision": "2026-11-20", "registradaEn": "2026-11-20T09:00:00", "aprobadaPor": "Directorio de Empresa Ficticia S.A.C.",
  "plazoDesignar": { "regla": "VACANCIA_MAXIMA", "unidad": "DIAS", "total": 30, "desde": "2026-11-20", "vence": "2026-12-20",
                     "diasRestantes": 30, "avance": 0, "estado": "EN_PLAZO", "estadoEtiqueta": "En plazo", "cuentaFeriados": false },
  "pendienteRemocionId": "9d0e…uuid",
  "plazoComunicarRemocion": { "regla": "PLAZO_COMUNICAR_REMOCION", "unidad": "DIAS_HABILES", "total": 5, "desde": "2026-11-20",
                     "vence": "2026-11-27", "…": "…" },
  "remocionComunicada": false,
  "hayAlternoComunicado": false }
```
`plazoComunicarRemocion` es `null` cuando ya se registró la comunicación (`remocionComunicada: true`). `hayAlternoComunicado` es
siempre `false` (alterno «Pronto»).

`pendientes`: lista de `Pendiente` (§10) **sin comunicar** de toda la empresa (cambios de datos y remociones).

---

## 4. Plazos en vivo

### `GET /plazos?regla=PLAZO_COMUNICAR_DESIGNACION&desde=2026-11-03` → `Plazo`
Para la tarjeta del paso 2 y del paso 4 (se recalcula al cambiar la fecha). `regla`: cualquier código de §11 con unidad
`DIAS_HABILES` o `DIAS`. `desde` obligatorio. 400 `OC_DATOS_INVALIDOS` si la regla no existe o la fecha no vale.

### `GET /plazos/remocion?fechaDecision=2026-11-20` → «Plazos que corren» de OC 12
```json
{ "comunicarRemocion": { "regla": "PLAZO_COMUNICAR_REMOCION", "vence": "2026-11-27", "…": "…" },
  "vacancia": { "regla": "VACANCIA_MAXIMA", "vence": "2026-12-20", "…": "…" },
  "alternoMaximoMeses": 4 }
```

---

## 5. Adjuntos

### `POST /adjuntos` (multipart) → 201 `Adjunto`
Partes: `ambito` (texto, código de `ambitosAdjunto`) y `archivo` (el fichero). Solo administradores.
- PDF, DOC o DOCX, hasta **10 MB**. Se valida la extensión **y** el contenido (firma `%PDF-`, cabecera OLE de DOC, estructura de
  Word en DOCX). Si no → 400 `OC_ADJUNTO_INVALIDO`.
- Mismo almacenamiento que las denuncias (`STORAGE_PROVIDER` local u Oracle Object Storage). El archivo se guarda con nombre
  generado (`oficial/<uuid-empresa>/<uuid>.pdf`); el nombre original solo como dato. SHA-256 guardado. Sin URL pública.
- Queda **suelto** hasta que una designación, corrección, comunicación o remoción lo use (por su `id`). Al usarlo se liga y ya no
  se puede borrar ni usar en otra cosa.

Qué ámbito pide cada campo (si no coincide → 400 `OC_DATOS_INVALIDOS` en ese campo):

| Campo | Ámbito |
|---|---|
| `designacion.adjuntoId` | `ACTA_DESIGNACION` |
| `designacion.hojaVidaAdjuntoId` | `HOJA_DE_VIDA` |
| `designacion.documentoVinculoAdjuntoId` | `DOCUMENTO_VINCULO` |
| `declaraciones.requisitos.adjuntoId` | `DJ_REQUISITOS_FIRMADA` |
| `declaraciones.designante.adjuntoId` | `DJ_DESIGNANTE_FIRMADA` |
| `constanciaAdjuntoId` (OC 8) | `CONSTANCIA_UIF` |
| `actaAdjuntoId` (OC 12) | `ACTA_REMOCION` |
| `constanciaAdjuntoId` de un pendiente | `CONSTANCIA_COMUNICACION` |

### `GET /adjuntos/{id}` → el archivo
`Content-Type` del archivo y `Content-Disposition: attachment; filename="<nombre original>"`. Errores en JSON. Reglas: misma empresa
(si no, 404); los de declaraciones solo para administradores (403 `OC_SIN_PERMISO`); los sueltos, solo administradores.

### `DELETE /adjuntos/{id}` → 204
Solo administradores y solo si está suelto (si no, 409 `OC_ADJUNTO_EN_USO`). Para «quitar» el archivo de una fila antes de guardar.

---

## 6. Declaraciones juradas

### `POST /declaraciones/vista-previa` → hoja de OC 5 (requisitos)
Cuerpo (datos del asistente; nada se guarda):
```json
{ "nombres": "Ana", "apellidos": "Prueba Torres", "tipoDocumento": "DNI", "numeroDocumento": "00000001",
  "tipoCargo": "TITULAR", "vinculo": "LABORAL", "fecha": "2026-11-03" }
```
Respuesta 200:
```json
{ "titulo": "DECLARACIÓN JURADA DE CUMPLIMIENTO DE REQUISITOS DEL OFICIAL DE CUMPLIMIENTO",
  "encabezado": "Yo, Ana Prueba Torres, identificado con DNI 00000001, designado como oficial de cumplimiento titular de Empresa Ficticia S.A.C., declaro bajo juramento que:",
  "puntos": [ { "letra": "a", "texto": "Tengo experiencia en las actividades de la empresa, en la lucha contra el LA/FT o como oficial de cumplimiento." },
              { "letra": "h", "texto": "Tengo vínculo laboral con la empresa y actuaré con autonomía e independencia." } ],
  "lugarFecha": "Lima, 03 de noviembre de 2026",
  "firmaNombre": "Ana Prueba Torres", "firmaDocumento": "DNI 00000001" }
```
- Campos vacíos se muestran como `[Nombres Apellidos]`, `[Documento]`, etc. (no da error: es una vista previa).
- `fecha` vacía = hoy. «Lima» va fijo, como en el diseño.

### `POST /declaraciones/vista-previa/pdf` → `application/pdf`
Mismo cuerpo; la misma hoja en PDF A4 (para imprimir, firmar y subir como `DJ_REQUISITOS_FIRMADA`). `Content-Disposition:
attachment; filename="declaracion-jurada-requisitos.pdf"`. El diseño no dibuja este botón: el front decide si lo muestra.

### `GET /designaciones/{id}/declaraciones/requisitos` → misma hoja con los datos guardados («Ver» de OC 9), más
`{ "firmada": true, "fechaFirma": "2026-11-03", "firmaNombre": "…", "adjunto": Adjunto|null, "vigente": true }`.
### `GET /designaciones/{id}/declaraciones/requisitos/pdf` → PDF de la hoja guardada.
### `GET /designaciones/{id}/declaraciones/designante`
```json
{ "titulo": "Declaración jurada de quién designó", "organo": "Directorio de Empresa Ficticia S.A.C.",
  "designaNombre": "Directorio u órgano equivalente",
  "documento": "Acta de sesión de directorio del 30/10/2026",
  "texto": "Declaro que la designación fue efectuada por la persona u órgano indicado y que adjunto copia del documento que la acredita.",
  "aceptada": true, "firmaNombre": "Luis Demo Rojas", "fechaFirma": "2026-11-03", "adjunto": null, "vigente": true }
```
El PDF de esta declaración es «Pronto» (el diseño no trae su texto completo): `…/designante/pdf` → 400 `OC_FUNCION_PRONTO`.

---

## 7. Designación

### `POST /designaciones` — «Guardar designación» (OC 6) → 201 `Ficha` (§8)
```json
{
  "tipoEmpresa": "PERSONA_JURIDICA",
  "tipoCargo": "TITULAR",
  "dedicacion": "NO_EXCLUSIVA",
  "persona": { "nombres": "Ana", "apellidos": "Prueba Torres", "nacionalidad": "PE", "tipoDocumento": "DNI",
               "numeroDocumento": "00000001", "fechaNacimiento": "1990-01-15", "domicilio": "Calle Ficticia 123, Lima",
               "telefono": "900000001", "correo": "ana.prueba@example.test" },
  "cargo": { "cargo": "Jefa de Cumplimiento", "fechaIngreso": "2022-03-15", "vinculo": "LABORAL",
             "direccionOficina": "Av. Ejemplo 456, Lima" },
  "designacion": { "designa": "DIRECTORIO", "nombreOrgano": "Directorio de Empresa Ficticia S.A.C.",
                   "documentoTipo": "ACTA_DIRECTORIO", "documentoFecha": "2026-10-30", "fechaDesignacion": "2026-11-03",
                   "adjuntoId": "uuid-acta", "hojaVidaAdjuntoId": null, "documentoVinculoAdjuntoId": null,
                   "esOficialOtraEmpresa": false, "reemplazaOficialAnterior": false },
  "declaraciones": {
    "requisitos": { "puntos": ["a","b","c","d","e","f","g","h"], "firmaNombre": "Ana Prueba Torres", "fechaFirma": "2026-11-03", "adjuntoId": null },
    "designante": { "aceptada": true, "firmaNombre": "Luis Demo Rojas", "fechaFirma": "2026-11-03", "adjuntoId": null } }
}
```
Valida en el servidor (400 `OC_DATOS_INVALIDOS` con `campos`):

| Campo | Regla |
|---|---|
| `tipoEmpresa`, `dedicacion` | Obligatorios, del catálogo. |
| `tipoCargo` | Obligatorio. Solo `TITULAR`; `ALTERNO`/`CORPORATIVO` → 400 `OC_FUNCION_PRONTO`. |
| `persona.nombres`, `persona.apellidos` | Obligatorios, ≤ 150. |
| `persona.nacionalidad` | Obligatoria, código ISO del catálogo. |
| `persona.tipoDocumento` / `numeroDocumento` | Obligatorios. DNI = 8 dígitos; CE = 6 a 12 letras o dígitos; PASAPORTE = 5 a 12 letras o dígitos. |
| `persona.fechaNacimiento` | Obligatoria, anterior a hoy. |
| `persona.domicilio` | Obligatorio, ≤ 255. |
| `persona.telefono` | Opcional, ≤ 30, solo dígitos, espacios, `+`, `-`, `(`, `)`. |
| `persona.correo` | Obligatorio, formato de correo, ≤ 150. |
| `cargo.cargo` | Obligatorio, ≤ 150. |
| `cargo.fechaIngreso` | Obligatoria, no futura. |
| `cargo.vinculo` | Obligatorio, del catálogo. |
| `cargo.direccionOficina` | Obligatoria, ≤ 255. |
| `designacion.designa` | Obligatorio y **permitido para el `tipoEmpresa`** (art. 7.1, tabla de §1). |
| `designacion.nombreOrgano` | Obligatorio, ≤ 255 (para una persona: su nombre). |
| `designacion.documentoTipo` | Obligatorio, del catálogo (se ofrecen los tres siempre). |
| `designacion.documentoFecha` | Obligatoria, no futura, no posterior a `fechaDesignacion`. |
| `designacion.fechaDesignacion` | Obligatoria, no futura. Desde aquí corren los 15 días hábiles (art. 7.2). |
| `designacion.adjuntoId` | Obligatorio (`ACTA_DESIGNACION`, suelto, de la empresa). |
| `designacion.hojaVidaAdjuntoId`, `documentoVinculoAdjuntoId` | Opcionales (los pide la solicitud SISDEL, art. 7.3; el diseño no los dibuja). |
| `designacion.esOficialOtraEmpresa` | `true` → 400 (art. 5.2: solo el corporativo, que es «Pronto»). |
| `designacion.reemplazaOficialAnterior` | Opcional. Si es `true` y hay un titular removido, queda ligado a él (`reemplazaAId`). |
| `declaraciones.requisitos.puntos` | Las 8 letras `a`–`h` (sin repetir). |
| `declaraciones.requisitos` firma | `firmaNombre` (≤ 255) **y** `fechaFirma` (no futura), **o** `adjuntoId` (`DJ_REQUISITOS_FIRMADA`). Pueden venir los dos. |
| `declaraciones.designante.aceptada` | `true`. |
| `declaraciones.designante` firma | Igual que la de requisitos (`DJ_DESIGNANTE_FIRMADA`). |

Además: 409 `OC_TITULAR_ACTIVO` si ya hay un titular pendiente, en evaluación o vigente. Lo que pone el servidor: estado
`PENDIENTE_COMUNICAR`, sello `guardadaEn` (hora de Lima), quién guardó, la empresa. Historial: «Designación guardada».
Si había un titular removido (cargo vacante), la vacancia termina.

### `PUT /designaciones/{id}` — corregir una designación aún no comunicada → 200 `Ficha`
Para «corrige los datos» tras un «No procedente» (OC 8) o para enmendar antes de comunicar. Solo con estado
`PENDIENTE_COMUNICAR` (si no → 409 `OC_ESTADO_INVALIDO`). Mismo cuerpo que el alta **más** `"motivo"` (obligatorio, ≤ 500).
Se reemplazan todos los datos; valida lo mismo; los adjuntos nuevos se ligan y los anteriores quedan guardados (no se borran).
Sin cambios → 400 `OC_SIN_CAMBIOS`. Historial: «Designación corregida» con `cambios` (antes → después). No crea pendiente con la
UIF (todavía no se le comunicó nada).

### `GET /designaciones` → lista de la empresa, más reciente primero
`[ ResumenDesignacion ]` (mismo objeto que `titular` de §3). Para llegar al historial de oficiales anteriores.

### `GET /designaciones/{id}` → `Ficha`

---

## 8. `Ficha` (OC 9; también la devuelven alta, corrección y comunicación)

```json
{
  "id": "c2a1…uuid", "estado": "VIGENTE", "estadoEtiqueta": "Vigente",
  "empresa": { "uuid": "0b6e…", "nombre": "Empresa Ficticia S.A.C." },
  "tipoEmpresa": "PERSONA_JURIDICA", "tipoEmpresaNombre": "Persona jurídica",
  "tipoCargo": "TITULAR", "tipoCargoNombre": "Titular", "dedicacion": "NO_EXCLUSIVA", "dedicacionNombre": "No exclusiva",
  "nombreCompleto": "Ana Prueba Torres", "iniciales": "AP",
  "etiquetas": [ { "codigo": "VIGENTE", "texto": "Vigente", "tono": "verde" },
                 { "codigo": "CODIGOS_ACTIVOS", "texto": "Códigos secretos activos", "tono": "lila" },
                 { "codigo": "TITULAR", "texto": "Titular", "tono": "gris" },
                 { "codigo": "DEDICACION", "texto": "Dedicación no exclusiva", "tono": "gris" } ],
  "persona": { "nombres": "Ana", "apellidos": "Prueba Torres", "nacionalidad": "PE", "nacionalidadNombre": "Perú",
               "tipoDocumento": "DNI", "tipoDocumentoNombre": "DNI", "numeroDocumento": "00000001", "documento": "DNI 00000001",
               "fechaNacimiento": "1990-01-15", "domicilio": "Calle Ficticia 123, Lima", "telefono": "900000001",
               "correo": "ana.prueba@example.test" },
  "cargo": { "cargo": "Jefa de Cumplimiento", "fechaIngreso": "2022-03-15", "vinculo": "LABORAL",
             "vinculoNombre": "Laboral (planilla)", "direccionOficina": "Av. Ejemplo 456, Lima" },
  "designacion": { "designa": "DIRECTORIO", "designaNombre": "Directorio u órgano equivalente",
                   "nombreOrgano": "Directorio de Empresa Ficticia S.A.C.",
                   "documentoTipo": "ACTA_DIRECTORIO", "documentoTipoNombre": "Acta de sesión de directorio",
                   "documentoFecha": "2026-10-30", "documentoTexto": "Acta de sesión de directorio del 30/10/2026",
                   "fechaDesignacion": "2026-11-03", "esOficialOtraEmpresa": false, "reemplazaAId": null,
                   "adjunto": { "id": "…", "ambito": "ACTA_DESIGNACION", "nombre": "acta-prueba.pdf", "…": "…" },
                   "hojaVida": null, "documentoVinculo": null },
  "declaraciones": {
    "requisitos": { "completa": true, "puntosAceptados": 8, "puntosTotal": 8, "firmaNombre": "Ana Prueba Torres",
                    "fechaFirma": "2026-11-03", "adjunto": null, "vigente": true },
    "designante": { "completa": true, "firmaNombre": "Luis Demo Rojas", "fechaFirma": "2026-11-03", "adjunto": null, "vigente": true } },
  "comunicacion": { "fechaComunicacion": "2026-11-05", "numeroSolicitud": "SISDEL-PRUEBA-0001",
                    "resultado": "PROCEDENTE", "resultadoNombre": "Procedente", "fechaNotificacion": "2026-11-12",
                    "motivoNoProcedente": null, "constancia": null, "registradaEn": "2026-11-12T15:30:00",
                    "codigos": { "reservados": true, "registradosEn": "2026-11-12T15:30:00", "cifradoDisponible": true } },
  "plazoComunicacion": null,
  "enElCargoDesde": "2026-11-03",
  "revisionSugerida": "2027-11-03",
  "requisitosCargo": [ "Experiencia en la actividad o en lucha contra el LA/FT",
                       "Sin condena por delito doloso ni destitución por falta grave",
                       "Sin deudas vencidas ni protestos pendientes, ni quiebra",
                       "No es ni fue auditor interno en los últimos 6 meses",
                       "Vínculo laboral directo, con autonomía e independencia" ],
  "remocion": null,
  "pendientes": [],
  "alterno": null,
  "guardadaEn": "2026-11-03T10:42:00", "guardadaPor": { "usuario": "admin.prueba", "nombreCompleto": "Ana Prueba Torres" },
  "permisos": { "puedeDesignar": false, "puedeCorregir": false, "puedeComunicar": true, "puedeCambiarDatos": true,
                "puedeRemover": true, "puedeRegistrarPendientes": true }
}
```
- `comunicacion`: `null` hasta la primera comunicación. **Los códigos nunca vuelven**: solo `codigos.reservados` (hay códigos
  guardados), `registradosEn` y `cifradoDisponible` (el servidor tiene la clave; si es `false`, mostrar «Códigos: no disponible»
  y no ofrecer escribirlos).
- `plazoComunicacion`: solo con `PENDIENTE_COMUNICAR` sin «No procedente» (ver §3).
- Etiquetas posibles: estado (`PENDIENTE_COMUNICAR` ámbar «Pendiente de comunicar», `EN_EVALUACION` lila «En evaluación»,
  `VIGENTE` verde, `REMOVIDA` rojo «Removida»), `CODIGOS_ACTIVOS` (solo si hay códigos y está vigente), `TITULAR`, `DEDICACION`.
- `requisitosCargo`: los cinco renglones del diseño; el quinto dice «Vínculo contractual directo, …» si el vínculo es contractual.
  Con «auditora/auditor» el diseño usa femenino: aquí va «auditor» (no sabemos el género).
- `revisionSugerida`: `fechaDesignacion` + `REVISION_SUGERIDA` (12 meses; deducido del diseño, por validar).
- `enElCargoDesde`: `fechaDesignacion` (como OC 11); `null` si está removida.
- `remocion` (si fue removida): `{ "id", "motivo", "motivoNombre", "razones", "aprobadaPor", "fechaDecision", "acta": Adjunto|null, "registradaEn", "registradaPor": Usuario }`.
- `pendientes`: los `Pendiente` de **esta** designación (comunicados y no), más reciente primero.
- `alterno`: siempre `null` («Pronto»).
- Para cuentas de solo lectura, `declaraciones.*.adjunto` llega `null`.

---

## 9. Comunicación a la UIF (OC 8)

### `POST /designaciones/{id}/comunicacion` → 200 `Ficha`
```json
{ "fechaComunicacion": "2026-11-05", "numeroSolicitud": "SISDEL-PRUEBA-0001",
  "resultado": "PROCEDENTE", "fechaNotificacion": "2026-11-12", "motivoNoProcedente": null,
  "codigoEmpresa": "XXXX", "codigoOficial": "YYYY", "constanciaAdjuntoId": "uuid-constancia" }
```
Se puede llamar varias veces sobre el **mismo registro** (dos momentos: presentada y respuesta; y corregir tras «No procedente»).

| `resultado` | Estados de origen | Pide | Efecto | Historial |
|---|---|---|---|---|
| `EN_EVALUACION` | `PENDIENTE_COMUNICAR`, `EN_EVALUACION` | `fechaComunicacion`, `numeroSolicitud` | → `EN_EVALUACION`; se cierra el plazo de 15 días. | «Comunicación presentada a la UIF» |
| `PROCEDENTE` | `PENDIENTE_COMUNICAR`, `EN_EVALUACION`, `VIGENTE` | lo anterior + `fechaNotificacion` | → `VIGENTE`. En `VIGENTE` sirve para agregar o cambiar códigos o la constancia. | «Designación procedente» (etiqueta «Cerrado»), o «Códigos secretos actualizados» si ya estaba vigente |
| `NO_PROCEDENTE` | `PENDIENTE_COMUNICAR`, `EN_EVALUACION` | lo anterior + `motivoNoProcedente` (≤ 1000) | → `PENDIENTE_COMUNICAR` con `observacionUif`; luego se corrige con `PUT /designaciones/{id}` y se vuelve a comunicar. | «Designación no procedente» con el motivo |

- Fechas: `fechaComunicacion` ≥ `fechaDesignacion` y no futura; `fechaNotificacion` ≥ `fechaComunicacion` y no futura.
  `numeroSolicitud` ≤ 50.
- **Códigos secretos** (`codigoEmpresa`, `codigoOficial`, ≤ 100 cada uno): opcionales; solo se aceptan con `PROCEDENTE` (art. 13.3:
  la UIF los asigna si procede); vacíos o ausentes = se conservan los que había. Se cifran con AES-256-GCM antes de guardar. Si el
  servidor no tiene `OC_CLAVE_CIFRADO` → **409 `OC_CIFRADO_NO_CONFIGURADO`** y no se guarda nada de esa llamada. Nunca se devuelven,
  nunca van al historial ni a los registros del servidor.
- `constanciaAdjuntoId`: opcional (`CONSTANCIA_UIF`); si llega, reemplaza a la anterior (la anterior queda guardada).
- Otros estados (p. ej. `REMOVIDA`, o `NO_PROCEDENTE` sobre `VIGENTE`) → 409 `OC_ESTADO_INVALIDO`.

---

## 10. Pendientes con la UIF (cambios de datos y remoción)

**`Pendiente`**
```json
{ "id": "9d0e…uuid", "tipo": "CAMBIO_DATOS", "designacionId": "c2a1…uuid",
  "titulo": "Comunicar el cambio de teléfono", "detalle": "Teléfono: 900 000 001 → 900 000 002",
  "plazo": { "regla": "PLAZO_COMUNICAR_CAMBIO", "unidad": "DIAS_HABILES", "total": 5, "desde": "2026-11-16", "vence": "2026-11-23", "…": "…" },
  "comunicado": false, "comunicacion": null,
  "creadoEn": "2026-11-16T10:05:00", "creadoPor": { "usuario": "admin.prueba", "nombreCompleto": "Ana Prueba Torres" } }
```
- `plazo` es `null` cuando `comunicado: true`. `comunicacion`: `{ "fecha": "2026-11-18", "numeroSolicitud": null, "nota": "…",
  "constancia": Adjunto|null, "registradaEn": "…", "registradaPor": Usuario }`.
- `detalle` del cambio **sí** lleva antes → después (es lo que hay que comunicar); se muestra a cuentas de la empresa, nunca en logs.
- Títulos: «Comunicar el cambio de <campo>» (uno) o «Comunicar el cambio de datos» (varios); «Comunicar la remoción».

### `GET /pendientes?todos=false` → `[Pendiente]` de la empresa (sin comunicar; con `todos=true`, también los comunicados).

### `POST /pendientes/{id}/comunicacion` — «Registrar comunicación» → 200 `Pendiente`
```json
{ "fecha": "2026-11-18", "numeroSolicitud": "SISDEL-PRUEBA-0002", "nota": "Presentado por SISDEL.", "constanciaAdjuntoId": null }
```
`fecha` obligatoria (no futura, no anterior al `desde` del pendiente); `numeroSolicitud` (≤ 50), `nota` (≤ 500) y la constancia
(`CONSTANCIA_COMUNICACION`) opcionales. Ya comunicado → 409 `OC_YA_COMUNICADO`. Historial: «Cambio de datos comunicado a la UIF»
o «Remoción comunicada a la UIF».

### `PATCH /designaciones/{id}/datos` — Cambiar datos (OC 10) → 200
Solo con estado `EN_EVALUACION` o `VIGENTE` (en `PENDIENTE_COMUNICAR` se usa la corrección `PUT`; si no → 409 `OC_ESTADO_INVALIDO`).
```json
{ "telefono": "900 000 002", "correo": null, "domicilio": null, "cargo": null, "direccionOficina": null,
  "fechaCambio": "2026-11-16", "motivo": "Cambio de línea telefónica corporativa." }
```
- Solo esos cinco campos; `null` o ausente = no se toca; texto vacío en `telefono` = se borra (los demás son obligatorios: vacío → 400).
  Mismas reglas de formato que el alta. Cualquier otro campo del JSON se ignora.
- `motivo` obligatorio (≤ 500) → 400 `OC_DATOS_INVALIDOS` con `campos.motivo`.
- `fechaCambio`: opcional, fecha en que **ocurrió** el cambio (art. 7.4); por defecto hoy; no futura ni anterior a la designación.
- Si nada cambia → 400 `OC_SIN_CAMBIOS`.
```json
{ "cambios": [ { "campo": "telefono", "etiqueta": "Teléfono", "antes": "900 000 001", "despues": "900 000 002" } ],
  "pendiente": { "id": "9d0e…", "tipo": "CAMBIO_DATOS", "plazo": { "regla": "PLAZO_COMUNICAR_CAMBIO", "vence": "2026-11-23", "…": "…" }, "…": "…" },
  "ficha": { "…": "Ficha actualizada" } }
```
Historial: «Cambio de datos: teléfono» con el motivo y los cambios; etiqueta «Pendiente de comunicar» hasta que se registre su
comunicación (luego «Comunicado»).

### `POST /designaciones/{id}/remocion` — Registrar remoción (OC 12) → 201
Solo con estado `EN_EVALUACION` o `VIGENTE` (si no → 409 `OC_ESTADO_INVALIDO`; una designación aún no comunicada se corrige con `PUT`).
```json
{ "motivo": "REMOCION_EMPRESA", "razones": "Cambio de responsabilidades dentro de la empresa.",
  "aprobadaPor": "Directorio de Empresa Ficticia S.A.C.", "fechaDecision": "2026-11-20", "actaAdjuntoId": "uuid-acta-remocion" }
```
| Campo | Regla |
|---|---|
| `motivo` | Obligatorio. `AUSENCIA_TEMPORAL` → 400 `OC_FUNCION_PRONTO`. |
| `razones` | Obligatorias, ≤ 2000 (art. 7.5: sustento). |
| `aprobadaPor` | Obligatoria con `REMOCION_EMPRESA` (art. 7.5); opcional en los otros. ≤ 255. |
| `fechaDecision` | Obligatoria, no futura, no anterior a la designación. Es la fecha de la decisión, del conocimiento del incumplimiento o de la vacancia: desde ella corren los 5 días hábiles y los 30 días. |
| `actaAdjuntoId` | Obligatorio con `REMOCION_EMPRESA`; opcional en los otros (`ACTA_REMOCION`). |

Efecto: la designación pasa a `REMOVIDA`, el cargo queda `VACANTE` (si era el titular), se crea el pendiente `REMOCION`. Historial:
«Remoción registrada» (etiqueta «Pendiente de comunicar» hasta registrar la comunicación).
```json
{ "designacionId": "c2a1…", "estadoEmpresa": "VACANTE",
  "remocion": { "id": "…", "motivo": "REMOCION_EMPRESA", "…": "…" },
  "pendiente": { "id": "…", "tipo": "REMOCION", "…": "…" },
  "plazos": { "comunicarRemocion": { "…": "Plazo" }, "vacancia": { "…": "Plazo" } } }
```
«Reemplazar» = remover y luego, en OC 13, «Designar nuevo oficial» (mismo asistente, `reemplazaOficialAnterior: true`).

---

## 11. Historial (OC 11)

### `GET /designaciones/{id}/historial` → 200
```json
{ "designacionId": "c2a1…", "nombreCompleto": "Ana Prueba Torres",
  "eventos": [
    { "id": "e7…", "tipo": "CAMBIO_DATOS", "titulo": "Cambio de datos: teléfono",
      "etiqueta": { "codigo": "PENDIENTE_COMUNICAR", "texto": "Pendiente de comunicar", "tono": "ambar" },
      "detalle": "900 000 001 → 900 000 002", "motivo": "Cambio de línea telefónica corporativa.",
      "cambios": [ { "campo": "telefono", "etiqueta": "Teléfono", "antes": "900 000 001", "despues": "900 000 002" } ],
      "usuario": { "usuario": "admin.prueba", "nombreCompleto": "Ana Prueba Torres" }, "fechaHora": "2026-11-16T10:05:00" },
    { "id": "e6…", "tipo": "DESIGNACION_PROCEDENTE", "titulo": "Designación procedente y códigos registrados",
      "etiqueta": { "codigo": "CERRADO", "texto": "Cerrado", "tono": "gris" },
      "detalle": "La UIF notificó que la designación es procedente. Se guardaron los códigos secretos.", "motivo": null, "cambios": [],
      "usuario": { "…": "…" }, "fechaHora": "2026-11-12T15:30:00" } ],
  "pendientesUif": [ { "…": "Pendiente sin comunicar de esta designación" } ],
  "vigencia": { "estado": "VIGENTE", "estadoEtiqueta": "Vigente", "enElCargoDesde": "2026-11-03", "revisionSugerida": "2027-11-03" } }
```
- Más nuevo primero. **Solo lectura**: no hay ruta para editar ni borrar eventos (ni métodos en el código).
- `etiqueta` de un `CAMBIO_DATOS` o `REMOCION` sale del estado de su pendiente (`PENDIENTE_COMUNICAR` ámbar o `COMUNICADO` verde);
  `DESIGNACION_PROCEDENTE` lleva `CERRADO`; los demás, `null`.
- Títulos: «Designación guardada» · «Designación corregida» · «Comunicación presentada a la UIF» · «Designación procedente» (o
  «Designación procedente y códigos registrados» si llegaron códigos) · «Designación no procedente» · «Códigos secretos actualizados»
  · «Cambio de datos: <campos>» · «Cambio de datos comunicado a la UIF» · «Remoción registrada» · «Remoción comunicada a la UIF».
- `detalle` nunca trae códigos secretos. `cambios` trae antes → después legible (fechas `dd/mm/aaaa`, nombres de catálogo).

---

## 12. Reglas y feriados (datos, no lógica fija)

### Reglas (`oc_regla`; las trae `GET /catalogos`)
| Código | Valor | Unidad | Artículo | Qué es |
|---|---|---|---|---|
| `PLAZO_COMUNICAR_DESIGNACION` | 15 | `DIAS_HABILES` | art. 7.2 | Comunicar la designación (desde la fecha de designación) |
| `PLAZO_COMUNICAR_CAMBIO` | 5 | `DIAS_HABILES` | art. 7.4 | Comunicar un cambio de datos (desde que ocurrió) |
| `PLAZO_COMUNICAR_REMOCION` | 5 | `DIAS_HABILES` | art. 7.5, 7.6, 6.2 | Comunicar la remoción o vacancia (desde la decisión) |
| `VACANCIA_MAXIMA` | 30 | `DIAS` | art. 7.6 | Máximo de vacancia |
| `ALTERNO_MAXIMO` | 4 | `MESES` | art. 8.2 c | Ausencia temporal cubierta por el alterno (Pronto) |
| `AVISO_AUSENCIA` | 5 | `DIAS_HABILES` | art. 8.2 b | Aviso previo de ausencia (Pronto) |
| `AVISO_PERDIDA_REQUISITO` | 5 | `DIAS_HABILES` | art. 6.2 | Aviso del oficial que deja de cumplir un requisito (informativo) |
| `REVISION_SUGERIDA` | 12 | `MESES` | — | Revisión sugerida de requisitos (deducido del diseño, por validar) |
| `UMBRAL_POR_VENCER` | 3 | `DIAS_HABILES` | — | Desde cuántos días restantes un plazo está «Por vencer» (DECISIONES) |

### `PUT /reglas/{codigo}` — solo administrador universal → 200 la regla
`{ "valor": 15 }` (entero 1–3650). Cambia los cálculos de todas las empresas desde ese momento. 400 `OC_DATOS_INVALIDOS`.

### `GET /feriados?anio=2026` → `[ { "fecha": "2026-12-25", "nombre": "Navidad", "fuente": "El Peruano, …" } ]`
### `POST /feriados` — solo administrador universal → 201 `{ "fecha": "AAAA-MM-DD", "nombre": "…", "fuente": "…" }`
`fecha`, `nombre` (≤ 150) y `fuente` (≤ 255, de dónde se confirmó) obligatorios; repetido → 409 `OC_FERIADO_DUPLICADO`.
### `DELETE /feriados/{fecha}` — solo administrador universal → 204 (404 si no existe).

La tabla **arranca vacía**: no se pudo abrir gob.pe ni El Peruano desde aquí para confirmar los feriados 2026, y la regla es no
sembrar lo que no se confirme. Mientras esté vacía, todos los `Plazo` dicen `cuentaFeriados: false`.

---

## 13. Qué queda «Pronto»

Guía «Requisitos y plazos del oficial de cumplimiento» (OC 0 «Ver requisitos y plazos» y la barra «Descargar PDF» de los cuatro
pasos): no hay ruta. Designar alterno (OC 9) y la tarjeta «Oficial alterno» (siempre «Sin alterno»). Cargos `ALTERNO` y
`CORPORATIVO`. Motivo `AUSENCIA_TEMPORAL`. PDF de la declaración del designante. Avisos por correo o en «Principal» cuando un
plazo se acerca o vence. «Abrir SISDEL» es un enlace externo del front (no pasa por el backend).

---

## 14. Diferencias con la norma (Res. SBS 789-2018, texto con la modificación SBS 02351-2023)

Donde la norma y el diseño difieren, se siguió `DECISIONES-OC.md` («norma primero»). Para aprobar:

1. **Punto h de la declaración (art. 6.1 h).** La norma dice «vínculo laboral **o contractual directo**»; el diseño escribe siempre
   «vínculo laboral». El texto del punto h (y el quinto renglón de «Requisitos del cargo») cambia según el vínculo elegido.
2. **Plazo de un cambio de datos (art. 7.4).** La norma lo cuenta «de **ocurrido** el cambio»; el diseño, «desde que guardas el
   cambio». Se agrega `fechaCambio` (por defecto hoy) y el plazo corre desde ella.
3. **Solicitud SISDEL (art. 7.3).** Pide también la **hoja de vida** y el **documento que acredite el vínculo**; el diseño no los
   dibuja. Se aceptan como adjuntos opcionales (`HOJA_DE_VIDA`, `DOCUMENTO_VINCULO`).
4. **Quién designa (art. 7.1).** El servidor exige que el designante corresponda al tipo de empresa (el diseño solo lo explica).
5. **Oficial de otra empresa (art. 5.2).** Solo el corporativo puede; como el corporativo es «Pronto», `esOficialOtraEmpresa: true`
   se rechaza.
6. **Remoción (art. 7.5).** «Aprobada por» y el acta son obligatorios con «Remoción por decisión de la empresa».
7. **Textos de la declaración que la norma dice distinto** (se dejaron como el diseño, según DECISIONES §2.3, para aprobar):
   punto a (la norma suma «o como trabajador en el área a cargo de un oficial de cumplimiento»); punto d (la norma suma «o en
   cobranza judicial» y «no aclarados a satisfacción de la SBS»).
8. **Códigos secretos (art. 13.3).** La UIF los entrega solo si la designación procede: por eso solo se aceptan con «Procedente».
   No son obligatorios (ni la norma ni el diseño lo dicen).
9. **Obligaciones (OC 9).** «Comunicar ROS a la UIF — EN 24 HORAS» y «Custodia de documentos — 5 AÑOS» no están en el art. 12; se
   muestran tal cual el diseño (tarjeta informativa).
10. **Gerente general como oficial (art. 5.5).** La norma pone condiciones (MEPECO, ≤ 10 trabajadores…); el diseño no las pide y el
    sistema no las valida.

## Cambios desde la primera versión

1. (2026-11-06) **CE**: acepta de 6 a 12 letras o dígitos (antes decía 9 a 12 dígitos), para no rechazar carnés antiguos.
2. (2026-11-06) La **`Ficha`** también trae `observacionUif` (`{ "motivo", "fechaNotificacion" }` o `null`), igual que `/estado`.
3. (2026-11-06) **Vista previa** (`POST /declaraciones/vista-previa` y su `/pdf`): solo administradores (es parte del asistente);
   otros roles → 403 `OC_SIN_PERMISO`.
4. (2026-11-06) `designacion.reemplazaAId` llega como el **uuid** de la designación reemplazada (o `null`).
