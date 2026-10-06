# Herramienta de carga de datos (`cargar_datos.py`)

Carga en `security_db` el catalogo de listas, las sancionadas de la SBS y las figuras politicas (con sus noticias), leyendo tus archivos en el momento.
**No guarda datos personales en el repositorio.** Es **segura por defecto**: sin `--aplicar` solo *simula* (lo hace todo dentro de una transaccion y la deshace) y te dice que cambiaria. Es idempotente y nunca borra.

| Comando | Que carga | Fuente |
|---|---|---|
| `listas` | 6 grupos de colorimetria + catalogo de listas (marca `es_pep` en las listas PEP) | `Listas y Colorimetría.xlsx` |
| `sancionadas` | Sujetos obligados sancionados por la SBS (lista *Peru - Personas sancionadas por la SBS*) | `Lista de empresas sancionadas.xlsx` |
| `politicos` | Figuras publicas del ML como entidades PEP + sus noticias (lista *Noticias*, marcadas "Pendiente de revision") | `--politicos-json` (ver abajo) o `--ml-url` |
| `todo` | listas + sancionadas (+ politicos si das la fuente) | |

## Uso (sin instalar nada: corre en Docker)

```bash
# 1) SIMULACION (no escribe). Cambia las rutas.
docker run --rm -v "C:\ruta\Documentos LAVADO DE ACTIVOS:/datos:ro" -v "%CD%:/repo" -w /repo python:3.12-slim sh -c \
  "pip install -q -r tools/requirements.txt && python tools/cargar_datos.py todo --carpeta /datos --db-url jdbc:mysql://host.docker.internal:3307/security_db --usuario root --clave root"

# 2) Para escribir de verdad, agrega  --aplicar
```
Sin `--db-url/--usuario/--clave` toma `DB_URL`, `DB_USER` y `DB_PASSWORD` del `.env` del backend (**cuidado: si ese `.env` apunta a Aiven, escribiras en la base compartida**).

## Politicos
Los tomamos del ML (Security-MachineLearning), que expone `GET /api/v1/personas?con_casos=true`:
`--ml-url http://host.docker.internal:8000 --ml-clave <INTERNAL_API_KEY>`. Si prefieres un archivo, `--politicos-json`.
Los politicos sin DNI se guardan con documento `SIN-DOC-<8 caracteres del id del ML>` y tipo de documento `SIN DOCUMENTO`; asi la carga es repetible.

## Antes de escribir en la base compartida (Aiven)
1. Haz un respaldo de las tablas que se tocan:
   `docker run --rm -e MYSQL_PWD mysql:8.0 mysqldump -h <host> -P <puerto> -u <usuario> --ssl-mode=REQUIRED --no-tablespaces security_db lista_grupo tipo_lista tipo_documento entidades personas_naturales personas_juridicas historial_manchas scoring_ocupacion scoring_departamento > respaldo.sql`
2. Corre la **simulacion** y revisa el resumen.
3. Repite con `--aplicar`.
