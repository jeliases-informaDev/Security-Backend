# Catalogos de scoring (fuente: libro vigente)

Generados del libro `Scoring y Factores_Riesgo LAFT.xlsm` (modificado el 2020-11-09; coincide con `Puntaje paises.xlsm`, 2020-11-23).
Los lee `ScoringCatalogSeeder` (solo con `APP_SEED_ENABLED=true`) y de ellos sale `docs/catalogos-scoring.sql`.

- `ocupaciones.csv`: hoja `FactorClientePN`, variable `Ocupacion_Profesion` (140 filas, puntaje 1-5) mas `INDEPENDIENTE`, `SIN EMPLEO` y `OTROS`,
  que en el libro son de la variable `Tipo_Actividad` (3, 2 y 4) pero el sistema ya las ofrecia como opciones.
- `departamentos.csv`: hoja `FactorZonaGeografica`, variable `Residencia`, departamentos de Peru (puntaje 1-5).

**Pendiente:** el libro corta los nombres a 30 caracteres (p. ej. `DERECHO DE LAS CIENCIAS ECONOM`). Se dejaron tal cual; solo se completo
`ANTROPOLOGO, ARQUEOLOGO, HISTORIADOR` porque ya existia con ese nombre. Cualquier correccion de texto debe hacerse aqui y en la base a la vez.
