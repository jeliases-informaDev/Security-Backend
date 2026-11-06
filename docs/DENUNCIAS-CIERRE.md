# Canal de denuncias: situaciones, cierre con resultado, derivación al oficial y plazo

Ampliación del módulo `denuncias` (2026-11-06). Todo es **aditivo**: las rutas y los campos de antes siguen igual. Todas las
rutas piden sesión (`Authorization: Bearer …`). Errores con `code` en el formato común (`ApiErrorResponse`).

## Quién puede qué

| Cuenta | Gestiona (listar, ver, cambiar estado, cerrar, derivar) | Ve lo reservado de la derivación |
|---|---|---|
| `ADMINISTRADOR` (universal de Sulotec) | todas | sí |
| `ADMINISTRADOR_EMPRESA` | las de su empresa | sí |
| `SUPERVISOR` | solo las asignadas a él (receptor) | **no** |
| Otras | no (403) | no |

Lo de otra empresa o no asignado responde **404** (igual que si no existiera). «Lo reservado» usa los mismos permisos de
escritura del módulo Oficial de Cumplimiento (`ADMINISTRADOR` y `ADMINISTRADOR_EMPRESA`), más un rol `OFICIAL_CUMPLIMIENTO` si
se crea más adelante (hoy no existe).

## `GET /api/denuncias/catalogo`

```json
{
  "situaciones": [
    { "codigo": "SOBORNO", "nombre": "Soborno o corrupción" },
    { "codigo": "CONFLICTO", "nombre": "Conflicto de intereses" },
    { "codigo": "ACOSO", "nombre": "Hostigamiento sexual y acoso" },
    { "codigo": "FRAUDE", "nombre": "Fraude" },
    { "codigo": "OPERACION_SOSPECHOSA", "nombre": "Operación sospechosa" },
    { "codigo": "OTRAS", "nombre": "Otras situaciones" }
  ],
  "estados": [
    { "codigo": "RECIBIDO", "nombre": "Recibida" }, { "codigo": "EN_PROCESO", "nombre": "En proceso" },
    { "codigo": "CERRADO", "nombre": "Cerrada" }
  ],
  "resultadosCierre": [
    { "codigo": "FUNDADA", "nombre": "Fundada", "pideMotivo": false },
    { "codigo": "INFUNDADA", "nombre": "Infundada", "pideMotivo": false },
    { "codigo": "ARCHIVADA", "nombre": "Archivada", "pideMotivo": true }
  ],
  "plazoAtencionDias": 30
}
```

`causa` en `POST /api/denuncias` acepta los 6 códigos.

## Cerrar con resultado: `PATCH /api/denuncias/{id}/estado`

Transiciones como antes: `RECIBIDO → EN_PROCESO → CERRADO`. Para pasar a `CERRADO`:

```json
{ "estado": "CERRADO", "resultadoCierre": "ARCHIVADA", "motivoCierre": "No hay elementos para continuar", "comentario": "opcional" }
```

- `resultadoCierre` obligatorio al cerrar → si falta: 400 `DENUNCIA_RESULTADO_OBLIGATORIO`.
- `motivoCierre` obligatorio solo con `ARCHIVADA` → 400 `DENUNCIA_MOTIVO_OBLIGATORIO`; hasta 1000 → 400 `DENUNCIA_MOTIVO_LARGO`.
- Mandar `resultadoCierre` o `motivoCierre` sin cerrar → 400 `DENUNCIA_RESULTADO_SOLO_AL_CERRAR`.
- Valor desconocido en `resultadoCierre` → 400 (JSON inválido). Transición no permitida → 400 (como antes, sin `code`).
- Responde 200 con el detalle. En el historial, el comentario del cierre lleva «Resultado del cierre: Archivada. Motivo: …».
- Las denuncias cerradas antes de este cambio quedan con `resultadoCierre: null` («sin dato»).

## Derivar al oficial: `POST /api/denuncias/{id}/derivar-oficial`

```json
{ "motivo": "opcional, hasta 500" }
```

- Puede hacerlo quien gestiona esa denuncia (tabla de arriba). 404 si es ajena o no asignada; 403 sin rol para gestionar.
- Motivo de más de 500 → 400 `DENUNCIA_MOTIVO_LARGO`. El cuerpo puede omitirse.
- **Idempotente**: si ya estaba derivada, responde 200 sin cambiar nada (no se des-deriva). Se puede derivar en cualquier estado.
- Responde 200 con el detalle (con lo reservado solo si la cuenta lo puede ver).
- En el historial queda una línea neutra, «Derivada para revisión reservada», **sin usuario** (`usuario: null`).

## Campos nuevos en el listado (`GET /api/denuncias`) y el detalle (`GET /api/denuncias/{id}`)

```json
{
  "causa": "FRAUDE", "causaEtiqueta": "Fraude",
  "estado": "EN_PROCESO", "estadoEtiqueta": "En proceso",
  "fechaLimiteAtencion": "2026-12-06",
  "diasParaVencer": 30,
  "vencida": false,
  "atendidaATiempo": null,
  "resultadoCierre": null, "resultadoCierreEtiqueta": null, "motivoCierre": null,
  "derivadaAlOficial": true
}
```

Solo en el detalle, para quien ve lo reservado (si no, `null`):

```json
"derivacion": { "fechaDerivacion": "2026-11-06T10:15:00", "derivadaPorUsuarioId": 12, "derivadaPor": "Ana Prueba", "motivo": "…" }
```

- `fechaLimiteAtencion` = fecha de creación + N días calendario. `diasParaVencer`: días que faltan (negativo = vencida hace N días;
  `null` si está cerrada). `vencida` = no cerrada y hoy > `fechaLimiteAtencion`. `atendidaATiempo` = cerrada con fecha de cierre
  ≤ `fechaLimiteAtencion` (`null` si sigue abierta).
- `derivadaAlOficial`: `true`/`false` solo para quien ve lo reservado; para los demás `null` (el supervisor no sabe si se derivó).
  En el resumen del Inicio solo sale la **cantidad** del mes.

## Plazo de atención (A CONFIRMAR)

`plaft.denuncias.plazo-atencion-dias` (variable de entorno `PLAFT_DENUNCIAS_PLAZO_ATENCION_DIAS`), por defecto **30 días
calendario**. Es un **supuesto a confirmar con el usuario**: el módulo no tenía plazo y la norma aplicable no está definida aquí.

## Base de datos

Sin pasos a mano. Columnas nuevas en `denuncias` (las crea Hibernate): `resultado_cierre` (VARCHAR 20), `motivo_cierre`
(VARCHAR 1000), `derivada_al_oficial` (BIT, 0 en las existentes), `fecha_derivacion`, `derivada_por_usuario_id`,
`motivo_derivacion` (VARCHAR 500). Hibernate 6+ crea las columnas de enumeración como `ENUM` de MySQL y `ddl-auto: update` no las
cambia: en una base vieja `causa` rechazaría los 3 valores nuevos. `EsquemaDenunciasInicializador`, al arrancar, mira
`information_schema` y, si `causa` (o `resultado_cierre`) es `ENUM`, la pasa a `VARCHAR` con `ALTER TABLE … MODIFY`, conservando
los valores y el NOT NULL. Es idempotente y un fallo se registra sin impedir el arranque. `estado` no cambia (sus valores siguen
siendo los mismos).

## Pruebas

`DenunciasCierreIntegracionTest` (MySQL del CI): catálogo, cierre sin resultado / sin motivo / motivo largo / válido, derivar
(sin permiso, ajeno, válido, idempotente, historial sin usuario), listado y detalle sin lo reservado para el supervisor, plazo
vencido y conversión de una columna `ENUM` vieja a texto sin perder datos.
