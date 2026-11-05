# Listas Negativas: consultas, porcentaje de coincidencia y clasificación

Este documento explica cómo PLAFT calcula el resultado de una consulta a Listas Negativas, dónde se cambian los pesos y qué
queda «Pronto». Las rutas, con ejemplos de cuerpo y respuesta, están en el contrato que acompaña el cambio (rutas bajo
`/api/listas-negativas/consultas`, controlador `ConsultaListasController`).

## 1. La consulta como unidad

Cada búsqueda que termina en una persona (o en ninguna) se guarda en `consultas_listas`: quién, cuándo, empresa (el nombre y el
UUID **del momento**), «¿A quién consultas?» (Cliente / Proveedor / Trabajador o director), lo escrito, la persona resultante, el
detalle por lista, el porcentaje, la clasificación y la decisión. El resultado completo se guarda en JSON (`resultado`): si mañana
cambian las reglas o se recarga una lista, la consulta, el historial y el PDF siguen mostrando lo que se calculó ese día.

- **Por documento:** búsqueda exacta. Una persona → consulta lista («Coincidencia exacta por DNI …»).
- **Por nombre** (o un documento repetido en la base): se devuelven candidatos; esa búsqueda se guarda con estado `BUSQUEDA`
  (no aparece en el historial) y al elegir a una persona se crea la consulta (`RESUELTA`, coincidencia `POR_NOMBRE`, con la
  advertencia «verifica que no sea un homónimo»). Volver a la lista y elegir a otra persona no cobra.
- **Sin resultados:** se guarda igual, como consulta sin coincidencias.
- **Tokens:** `POST /consultas` consume `busqueda_listas` una vez, con la misma clave de dos minutos que el viejo `GET /buscar`
  (hacer las dos cosas, o repetir, en ese plazo no cobra dos veces). Ver candidatos, elegir, abrir una consulta, el historial y el
  homónimo no consumen. El PDF de la consulta consume `lista_pdf`. No hay cobros nuevos.
- **Visibilidad:** la de siempre (`VisibilidadEmpresaService`): cuenta común = lo suyo; administrador de empresa = su empresa;
  administrador universal = todo. La empresa que se compara es la **guardada en la consulta**, no la que la cuenta tenga hoy.
  Una consulta de otra empresa responde 404, igual que una que no existe.

## 2. Reglas como datos

Tablas (se crean solas con `ddl-auto: update`):

| Tabla | Qué guarda |
|---|---|
| `listas_grupo_peso` | Grupos de peso: `CRITICA` 15, `ALTA` 10, `INFORMATIVA` 5, `SIN_PESO` 0. `critica = true` hace «Coincidencia crítica». |
| `listas_regla` | Las diez filas del «Detalle por lista»: nombre, subtítulo, orden, chip (`fuente`), `grupo_peso`, `estado` (`CONSULTABLE` / `PRONTO`) y su motivo, `todas_pep`, `tipo_conteo` (`REGISTROS` / `NOTAS` / `NOTICIAS`) y las etiquetas del aviso y de la suma. |
| `listas_regla_origen` | Las listas (`tipo_lista.nombre`) que alimentan cada fila. Se comparan sin mayúsculas, tildes ni espacios de más. |
| `listas_regla_otras` | «Otras listas»: grupo de colorimetría (`lista_grupo.orden`) → grupo de peso. |

Los valores iniciales están en `src/main/resources/listas/reglas-listas.json`. `ReglasListasInicializador` los siembra **siempre al
arrancar** (también con el seed apagado), crea solo lo que falta (por código; «otras» por grupo) y **nunca pisa lo cambiado a mano**.
Al arrancar avisa en el log cada lista de las reglas que no existe en `tipo_lista` («la fila X usa la lista 'Y', que no existe»).

**Dónde se cambian los pesos:** en `listas_grupo_peso.peso` (cambia todas las filas de ese grupo). Para mover una fila de grupo,
`listas_regla.grupo_peso`. Para encender una fila «Pronto», poner `estado = 'CONSULTABLE'` y agregar sus listas en
`listas_regla_origen`. Editar el JSON no cambia una base que ya tiene las filas.

Filas iniciales:

| Fila | Grupo (peso) | Listas | Estado |
|---|---|---|---|
| PEP · Perú | Alta (10) | todas las listas con `es_pep` | consultable |
| SUNAT | Sin peso (0) | — | **Pronto**: no hay fuente de datos del contribuyente |
| ONU · Res. 1267 | Crítica (15) | `Internacional - Consejo de Seguridad ONU` | consultable |
| OFAC | Crítica (15) | `Lista OFAC` (no la de eliminados) | consultable |
| Unión Europea | Crítica (15) | `Internacional - Sanciones de la Unión Europea` | consultable |
| ONU · 1718 y 1737 | Crítica (15) | — | **Pronto**: la lista del Consejo cargada no separa esos regímenes |
| ASBANC · Investigados | Alta (10) | `ASBANC - Investigados` | consultable |
| ASBANC · Noticias | Informativa (5) | `ASBANC - Noticias (información negativa)` | consultable |
| GAFI | Alta (10) | — | **Pronto**: el GAFI publica países, no personas, y la consulta no pide país |
| Noticias en medios | Informativa (5) | `Noticias` | consultable |

Una fila consultable cuyas listas no existen en la base también queda «Pronto» (motivo «ninguna de las listas de esta fila está
cargada»). **Una fila «Pronto» nunca dice «Sin coincidencias»** y no cuenta en «de M listas».

## 3. El cálculo (`CalculoListas`, función pura)

1. Cada aparición de la persona va a la primera fila consultable que la alimenta (PEP por `es_pep`; las demás por nombre de lista).
2. Si ninguna fila la alimenta, **no se pierde**: va a «Otras listas», una fila por lista, con el peso del grupo de colorimetría
   de su lista (1 → crítica 15; 2 y 3 → alta 10; 4, 5 y 6 → informativa 5; sin grupo → el grupo de menor peso, 0).
3. **Porcentaje** = suma de los pesos de las filas con al menos un registro (no importa cuántos), con **tope 100**.
4. **Clasificación:** sin filas con registros → `SIN_COINCIDENCIAS`; alguna fila de un grupo `critica` con registros → `CRITICA`;
   si no → `MEDIA`. No hay más niveles (criterio por validar con el dueño: se cambia en `listas_grupo_peso.critica`).
5. «**N de M listas**»: M = filas consultadas de verdad (sin las «Pronto»; las de «Otras listas» solo cuando aparecen); N = filas con registros.
6. «X registros en listas · Y noticias en medios»: Y = registros de las filas `NOTICIAS`; X = el resto.
7. Aviso: título («Coincidencia detectada» / «Coincidencia crítica» / «Sin coincidencias en las M listas consultadas»), fuentes con
   coincidencia y «Suma N% (PEP 10 + investigados 10 + noticias 5 + 5): clasificación Media.» (filas seguidas con la misma etiqueta se juntan).

Ejemplos del diseño (pruebas en `CalculoListasTest`): PEP 10 + investigados 10 + ASBANC noticias 5 + noticias 5 = **30 → media**;
ONU 1267 15 + ONU 1718/1737 15 + OFAC 15 + UE 15 + investigados 10 + noticias 5 = **75 → crítica** (este segundo solo como prueba:
en producción ONU 1718/1737 está «Pronto»).

## 4. Es homónimo

Lo puede marcar quien puede ver la consulta, con comentario (mínimo 3 letras), solo si tiene coincidencias. Queda con usuario y
fecha y la consulta pasa a «Descartada · homónimo» (el porcentaje guardado no cambia). Se puede deshacer; cada marca y cada deshacer
quedan en `consultas_listas_decision` (quién, cuándo, comentario).

**Scoring:** si la empresa de quien evalúa tiene marcada a esa persona (`id_entidad`) como homónimo (la marca vigente más reciente;
una cuenta sin empresa, solo sus propias consultas), el paso 1 (`GET /api/scoring/cliente` y la evaluación) cuenta 0 en PEP,
sanciones y noticias, no aplica las reglas PEP ni SANCIONES y devuelve `consulta.homonimo.texto`
(«Descartado como homónimo el dd/mm/aaaa por …»). El PDF del scoring también lo menciona.

## 5. Historial y página principal

- `GET /consultas`: de a 10, más reciente primero; pestañas `TODAS`, `CON_COINCIDENCIAS`, `SIN_COINCIDENCIAS`, `CRITICAS`; búsqueda por
  nombre, documento o lo escrito, todo en la base.
- `GET /consultas/resumen?mes=AAAA-MM`: consultas del mes, con coincidencia, críticas (y críticas sin descartar) y sin coincidencias.
- `GET /consultas/inicio`: las 3 últimas («Últimas búsquedas») y la rueda = el porcentaje más alto de esas 3 (`null` sin consultas).
- **Historial viejo:** `historial_consultas` se deja en su tabla y se sigue leyendo con `GET /api/listas-negativas/historial`. No se
  copia al historial nuevo porque no guarda lo escrito, el tipo de consulta ni el resultado.

## 6. Fecha de última carga

`tipo_lista.ultima_carga` (columna nueva, opcional). La debe poner quien carga los datos (el cargador masivo de `Sulotec-infra` hoy
no la escribe). Con fecha, la fila muestra «ACT. dd MMM» y el encabezado la fecha más reciente; sin fecha no se muestra nada.

## 7. Lo que no se inventa (queda «Pronto» o sin mostrar)

- SUNAT, GAFI y ONU 1718/1737: filas «Pronto» hasta que haya una fuente de datos real.
- «Medio verificado» / «solo medios de la lista autorizada»: no hay lista de medios autorizados; las noticias van con su enlace,
  su dominio y su fecha guardada, sin esa etiqueta. La fecha es la del registro (no se saca del enlace).
- Programar búsqueda y Consulta masiva: «Pronto».
- Con licencia vencida (solo lectura) `POST /consultas` queda bloqueado como toda escritura, porque ahora guarda la consulta.
