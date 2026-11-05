# Catalogos de scoring

Los lee `ScoringCatalogoInicializador` **siempre al arrancar** (tambien con `APP_SEED_ENABLED` apagado). Crea lo que falta y
completa lo vacio; nunca pisa lo que alguien cambio a mano en la base. La metodologia completa esta en `docs/SCORING.md`.
Separador `;`, primera fila de encabezado, UTF-8.

| Archivo | Columnas | Fuente |
|---|---|---|
| `ocupaciones.csv` | `nombre;puntaje;puntaje_v2` | `puntaje` (1-5, version 1): libro `Scoring y Factores_Riesgo LAFT.xlsm` (2020), hoja `FactorClientePN`, mas `INDEPENDIENTE`, `SIN EMPLEO` y `OTROS`. `puntaje_v2` (1-4): metodologia del 29-oct-2026; vacio = sin puntaje (elegirla bloquea el calculo). 35 de 143 tienen puntaje. |
| `departamentos.csv` | `nombre;puntaje;puntaje_v2` | `puntaje`: libro de 2020, hoja `FactorZonaGeografica`. `puntaje_v2`: metodologia 2026 (los 25 tienen). |
| `productos.csv` | `nombre;puntaje` | metodologia 2026 (12 opciones). |
| `canales.csv` | `nombre;puntaje` | metodologia 2026 (5). |
| `medios-pago.csv` | `nombre;puntaje;pide_origen_fondos` | metodologia 2026 (7); `si` en Efectivo. |
| `paises.csv` | `nombre;grupo;alto_riesgo_gafi;vigencia;otros_nombres` | clasificacion inicial al 2026-10-30 (plenario del GAFI de octubre de 2026). `*` = todos los demas paises (grupo 3). `otros_nombres` separados por barra vertical para emparejar con la tabla `pais`. |

Como se empareja: por nombre sin tildes, sin mayusculas y sin espacios (`EMPRESARIO EXPORTADOR/ EMPRESA` = `EMPRESARIO EXPORTADOR/EMPRESA`).
Antes de clasificar, la tabla `pais` se completa con `paises-iso.csv` (`alfa2,alfa3,numerico,nombre_es`, 250 paises, el mismo
catalogo ISO que usa la carga de listas en Sulotec-infra): se agregan los que faltan por nombre normalizado, sin tocar ni borrar
los que ya estan. Si un pais de `paises.csv` igual no aparece en la tabla, el log lo avisa. Una fila de `paises.csv` con
`vigencia` mas nueva que la guardada reemplaza a la guardada (asi se actualiza la lista del GAFI).

**Pendiente:** el libro corta los nombres a 30 caracteres (p. ej. `DERECHO DE LAS CIENCIAS ECONOM`). Se dejaron tal cual; cualquier
correccion de texto debe hacerse aqui y en la base a la vez. `docs/catalogos-scoring.sql` es de la version 1 y ya no hace falta.
