# Scoring de riesgo LA/FT (metodología versión 2, 29-oct-2026)

Lo que quedó programado en el backend. Todas las tablas y cifras son **propuesta a validar con el oficial de cumplimiento**.
Las evaluaciones hechas antes de este cambio quedan como **versión 1**: se siguen mostrando con su puntaje y su categoría de
entonces (`Riesgo Muy Bajo` … `Riesgo Muy Alto`) y no admiten decisión.

## 1. Dónde vive cada cosa

| Qué | Dónde se cambia |
|---|---|
| Pesos de grupos y factores, bandas (Bajo/Medio/Alto), rangos de monto, puntajes fijos (PEP 4, sin coincidencias 1, historial 1), umbral de la regla 5, grupos de Listas Negativas que cuentan como «sanciones o LA/FT», qué hacer por nivel | **Un solo lugar:** `MetodologiaV2.ACTUAL` en `src/main/kotlin/com/security/modules/scoring/domain/metodologia/Metodologia.kt`. Si cambian, sube la `version`. |
| Puntaje de cada ocupación y departamento | columna `puntaje_v2` de `scoring_ocupacion` y `scoring_departamento` (vacío = sin puntaje). Valor inicial: `src/main/resources/scoring/ocupaciones.csv` y `departamentos.csv`. |
| Producto, canal y medio de pago | tabla `scoring_opcion` (`tipo` = PRODUCTO / CANAL / MEDIO_PAGO). Valor inicial: `productos.csv`, `canales.csv`, `medios-pago.csv`. |
| Grupo de cada país y marca del GAFI | tabla `scoring_pais_grupo` (uno por país de la tabla `pais`, con `vigencia`). Valor inicial: `paises.csv`. La tabla `pais` se completa al arrancar con `paises-iso.csv` (250 países ISO; agrega los que faltan, no toca los que hay). |
| El cálculo | `CalculoScoring` (función pura, sin base ni red) y `ClasificadorListas` en el mismo paquete. |

Los catálogos se cargan solos al arrancar (`ScoringCatalogoInicializador`), **también con `APP_SEED_ENABLED` apagado**. Crean lo
que falta y completan lo vacío; no pisan lo cambiado a mano. Los puntajes viejos (1 a 5, columna `puntaje`) no se tocan.
Detalle de los archivos en `src/main/resources/scoring/LEEME.md`.

## 2. Factores y fórmula

| Grupo (peso) | Factor | Peso | De dónde sale |
|---|---|---|---|
| Clientes (60 %) | Ocupación o actividad | 25 % | formulario (`ocupacionId`) |
| | Condición del cliente | 10 % | automático: Listas Negativas (PEP = 4, si no 1) |
| | Monto y frecuencia | 20 % | formulario + Registro de Operaciones |
| | Historial en la relación | 5 % | automático: 1 «Sin alertas» (no existe aún el registro de alertas) |
| Productos y servicios (20 %) | Producto o servicio | 10 % | formulario |
| | Canal y medio de pago | 10 % | formulario: el **mayor** de los dos |
| Zona geográfica (20 %) | Departamento | 10 % | formulario: el **mayor** entre residencia y operación (los dos son obligatorios) |
| | País de los fondos | 10 % | formulario: el grupo del país (1 a 4) |

Promedio de grupo = Σ(peso × puntaje) ÷ peso del grupo. Puntaje = 0,60 × Clientes + 0,20 × Productos + 0,20 × Zona, calculado
sin redondeos intermedios y redondeado a dos decimales (al más cercano). El nivel se decide con el valor redondeado:
Bajo < 1,75 ≤ Medio < 2,50 ≤ Alto. Ejemplo de control (probado): 3,75 · 2,00 · 1,50 → 2,95 · Alto, reglas `PEP` y `DOS_FACTORES_EN_4`.

**Monto:** hasta S/ 20 000 → 1; hasta 100 000 → 2; hasta 500 000 → 3; más → 4. Se toma el mayor entre el monto de la operación
y el promedio mensual del cliente en el Registro de Operaciones: suma de los últimos 12 meses ÷ 12, solo de la **empresa de quien
evalúa** (la empresa de la cuenta dueña de cada registro), ubicando al cliente por su número de documento como persona que
realiza la operación o en cuyo nombre se realiza. Solo se suman operaciones en soles (moneda que empieza con `PEN` o dice
«Soles»): no hay tipo de cambio. Si la cuenta no tiene empresa, el cliente no tiene documento, no hay operaciones o la consulta
falla, se usa solo el monto de la operación y `montoNota` lo dice.

**Listas Negativas** (`ClasificadorListas`): PEP = listas con `es_pep`; noticias y medios = listas cuyo nombre contiene
«NOTICIA»; sanciones o LA/FT = las demás listas de los grupos con `orden` 1 a 4 (restrictivas, LA/FT o corrupción penal y
administrativo, sanciones administrativas). Afectación financiera (5) e informativas (6) no cuentan. **Parientes, cónyuge y
empresas vinculadas a un PEP no se pueden comprobar** (la base no tiene ese dato): van con `revisado: false` («Sin dato»).

## 3. Reglas

1. `PEP`: cliente PEP → Alto. (Pariente, cónyuge o empresa con PEP al 25 %: no comprobable todavía, ver arriba.)
2. `SANCIONES`: alguna coincidencia en listas de sanciones o LA/FT → Alto.
3. `PAIS_GAFI`: país de los fondos con `alto_riesgo_gafi` → Alto.
4. `DOS_FACTORES_EN_4`: dos o más factores con puntaje 4 → Alto.
5. `MONTO_ALTO_OCUPACION_BAJA`: ocupación de puntaje 1 o 2 y más de S/ 100 000 **en la operación** (no el promedio) → Alto.
6. Falta un dato obligatorio, o la ocupación, un departamento, el producto, el canal, el medio de pago o el país no tienen
   puntaje → **sin nivel**: `bloqueos` dice qué falta; `POST /evaluar` responde 422 y no guarda ni consume.

Las reglas 1 a 5 solo suben el nivel; nunca levantan un bloqueo.

| Nivel | Debida diligencia | Revisión | Quién decide |
|---|---|---|---|
| Bajo | Simplificada o regular | 24 meses | Analista |
| Medio | Regular | 12 meses | Analista |
| Alto | Reforzada | 6 meses | Gerencia más alta o comité |

## 4. Rutas (`/api/scoring`, todas con sesión)

- `GET /catalogos`: metodología, ocupaciones, productos, canales, mediosPago (`pideOrigenFondos` en Efectivo), departamentos,
  paises (Perú primero), `sustentoDisponible: true`, `bandas`, `grupos` (salen de `MetodologiaV2`) y `vigenciaPaises` (extra).
- `GET /cliente?documento=…[&tipoDocumento=DNI]`: consulta Listas Negativas (no consume tokens).
- `POST /vista-previa`: acepta datos a medias; no guarda ni consume.
- `POST /evaluar`: guarda y consume 1 token `scoring` (se pide antes de guardar y se anula si el guardado falla). El cliente puede
  ser una entidad de la base de listas (`entidadId`) o uno nuevo (`cliente`: tipoPersona NATURAL/JURIDICA, tipoDocumento
  DNI/CE/RUC/PASAPORTE, documento y nombre), que se guarda con la evaluación y **no** se crea en la base de listas (común a todas
  las empresas). Su documento igual se consulta en las listas.
- `GET /{id}`, `GET /{id}/pdf`, `POST /{id}/decision`, `GET /historial`: con la misma visibilidad que el historial (cuenta común:
  lo suyo; administrador de empresa: su empresa; administrador universal: todo). Lo ajeno responde 404 como si no existiera.
- `POST /sustentos` (multipart, campo `archivo`, PDF/JPG/PNG por contenido, hasta 10 MB) → `{ "id" }`. Se guarda con el mismo
  almacenamiento del canal de denuncias (`STORAGE_PROVIDER`), en `scoring/sustentos/`. Nunca bloquea el cálculo.

**Decisión** (`{ "accion", "comentario" }`):
- `APROBAR`: solo con Bajo o Medio (decide el analista). Con Alto → 409 `SCORING_REQUIERE_APROBACION`.
- `ENVIAR_A_APROBACION`: solo con Alto (decide la gerencia más alta o un comité). Con Bajo o Medio → 409 `SCORING_NO_REQUIERE_APROBACION`.
- `RECHAZAR`: con cualquier nivel.
- Con Alto el comentario es obligatorio (400 `SCORING_COMENTARIO_OBLIGATORIO`). Ya decidida → 409 `SCORING_YA_DECIDIDO`.
  Versión 1 → 409 `SCORING_VERSION_ANTIGUA`. Acción desconocida → 400 `SCORING_ACCION_INVALIDA`.

Errores: `{ timestamp, status, error, message, path, code, bloqueos? }`.

## 5. Qué guarda cada evaluación (tabla `scoring_riesgo`)

Columnas nuevas, todas opcionales: `version_metodologia` (por defecto 1), `nivel`, `nivel_por_promedio`, `fijado_por_regla`,
`cliente_*`, `id_departamento_operacion`, `id_producto`, `id_canal`, `id_medio_pago`, `id_pais_fondos`, `monto_operacion`,
`promedio_mensual`, `id_sustento`, `resultado` (el resultado completo en JSON: factores con dato, puntaje, peso y aporte, reglas,
consulta a listas y qué hacer) y `decision_*` (acción, comentario, quién y cuándo). `id_entidad` queda vacío para un cliente nuevo.

## 6. Pendiente

- Validar con el oficial de cumplimiento todas las tablas, la lectura de «sanciones o LA/FT» como grupos 1 a 4 (el grupo 3
  incluye listas informativas peruanas como «No hallados SUNAT»), la regla de noticias y la clasificación de países.
- Parientes, cónyuge y empresas vinculadas a un PEP: no hay fuente de datos.
- Historial en la relación: falta la tabla de antigüedad y alertas.
- Tipo de cambio para sumar operaciones en dólares o euros.
- Umbral de registro del sustento de fondos (llegará con la configuración de sector y umbrales del Registro de Operaciones).
- La lista del GAFI cambia tres veces al año: actualizar `paises.csv` con la nueva `vigencia` (reemplaza las filas con vigencia
  más vieja) o editar `scoring_pais_grupo` poniendo la fecha del cambio en `vigencia`.
