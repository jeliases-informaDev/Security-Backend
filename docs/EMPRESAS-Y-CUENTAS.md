# Empresas y cuentas

Cada cuenta (tabla `usuarios`) pertenece a una empresa: la columna `usuarios.empresa` guarda su nombre.

## Para qué sirve

El módulo de **Registro de operaciones** trabaja por empresa:

- `GET /api/operaciones/resumen?empresa=...` devuelve los registros cuyo dueño (`id_usuario`) es una cuenta de esa empresa.
- El responsable de un registro solo puede ser otra cuenta de la misma empresa.
- La web pide la empresa de la cuenta que entró (`/api/operaciones/obtener-usuario`) y una cuenta sin empresa no puede usar el módulo.

Así cada cliente ve solo lo suyo.

## La lista de empresas

Tabla `empresas` (la crea Hibernate al arrancar, `ddl-auto: update`):

| Columna | Detalle |
|---|---|
| `nombre` | Obligatorio y único sin importar mayúsculas. **No se cambia.** |
| `ruc` | Opcional, 11 dígitos, único. |
| `activa` | Una empresa inactiva ya no se ofrece al crear cuentas; las cuentas que ya tiene siguen funcionando. |

Reglas:

- Al **crear una cuenta** la empresa es obligatoria, debe estar en la lista y estar activa. Se guarda con el nombre exacto de la lista, así que nadie queda con la empresa mal escrita.
- Una cuenta se puede **pasar a otra empresa** (mismas reglas). Los registros de operaciones que ya hizo van con ella, porque la empresa de un registro sale de su dueño.
- Para corregir una empresa mal escrita: crear la correcta, pasar las cuentas a ella y desactivar la anterior.
- Al arrancar, `EmpresaCatalogoInicializador` agrega a la lista las empresas que ya tengan escritas las cuentas existentes y, si la lista sigue vacía (base nueva), la empresa `Empresa Demo`. Es idempotente y no modifica las cuentas.

## Endpoints

Todos exigen el rol `ADMINISTRADOR`.

| Método y ruta | Qué hace |
|---|---|
| `GET /api/empresas` | Lista las empresas con su RUC, estado y cuántas cuentas tiene cada una. |
| `POST /api/empresas` | Agrega una empresa: `{ "nombre": "...", "ruc": "20123456789" }` (RUC opcional). |
| `PUT /api/empresas/{id}` | Cambia el RUC: `{ "ruc": "..." }` (vacío lo borra). |
| `PATCH /api/empresas/{id}/estado?activa=true\|false` | Activa o desactiva la empresa. |
| `POST /api/usuarios` | Crea una cuenta; ahora exige `empresa` (de la lista). |
| `PUT /api/usuarios/{id}/empresa` | Pasa la cuenta a otra empresa: `{ "empresa": "..." }`. |
| `GET /api/usuarios/roles` | Roles activos, para elegir el de una cuenta nueva. |

## A tener en cuenta

- Hoy **todo ADMINISTRADOR ve y administra todas las empresas**. Si más adelante un cliente tiene su propio administrador, habrá que limitarlo a su empresa.
- `GET /api/usuarios` devuelve todas las cuentas solo a un administrador; a una cuenta común, las de su empresa (ver "Aislamiento").

## Identificadores, carga masiva e invitaciones

- Cada empresa tiene un **UUID** (identificador público, no adivinable) y una **sigla** (2 a 8 letras o números, única; se propone a partir del nombre y se puede indicar al crear). A las empresas anteriores se les asignan al arrancar.
- El **usuario interno** de cada cuenta lleva la sigla delante: `hcyo.jperez`. Así dos empresas pueden tener un "jperez" sin chocar; en pantalla se ve el nombre de la persona.
- Se puede **entrar con el correo o con el usuario**: el correo es único entre empresas.
- **Carga masiva** (`POST /api/usuarios/carga-masiva`, solo ADMINISTRADOR): `{ "empresa": "...", "soloValidar": true|false, "cuentas": [{ "nombres", "apePat", "apeMat"?, "correo", "cargo"?, "rolCodigo" }] }`. Primero se revisa fila por fila (correo válido y no repetido, rol existente); se crea **todo o nada**. El usuario se arma solo (`sigla.parte-local-del-correo`).
- **Invitaciones:** con correo de salida configurado (`SPRING_MAIL_HOST` y compañía en `plaft.env`; remitente `APP_MAIL_FROM`), cada cuenta nueva recibe un enlace de un solo uso (72 h) a `{WEB_URL}/restablecer-clave?token=...` para elegir su clave; también `POST /api/usuarios/{id}/invitar` para reenviarlo. Sin correo configurado, la carga devuelve una **clave temporal** por cuenta para entregarla a mano y los enlaces quedan en el log.
- Recuperación: `POST /api/auth/forgot-password` (correo) y `POST /api/auth/reset-password` (token + nueva clave) ya existían; la web ahora tiene las pantallas.

## Aislamiento: la empresa sale de la cuenta, no del pedido

`AccesoEmpresaService` (módulo de operaciones) decide con qué empresa se trabaja a partir de la **cuenta autenticada** (el token), nunca de lo que mande la web o la app:

| Quién | `empresa=` / `usuario=` en el pedido | Registros por id (`GET`, `PUT`, PDF, `PATCH /usuario`) | Registro nuevo |
|---|---|---|---|
| Cuenta común | Se ignoran: siempre su propia empresa y su propia cuenta. Sin empresa → 403 | Solo los de su empresa (la del dueño del registro); uno ajeno responde **404** | Queda a su nombre y en su empresa, aunque el cuerpo diga otra cosa |
| `ADMINISTRADOR` | Puede indicar cualquier empresa o cuenta; sin indicar, la suya | Todos | Como lo mande (a nombre de la cuenta indicada) |

**Hasta dónde ve cada cuenta** (`VisibilidadEmpresaService`, regla general):

| Rol | Alcance | Búsquedas (historial), scoring, matrices | Denuncias | Operaciones |
|---|---|---|---|---|
| Cuenta común (USUARIO, SUPERVISOR) | **Propio** | Solo lo que registró ella misma | SUPERVISOR: solo las asignadas a él | Todo lo de su empresa |
| `ADMINISTRADOR_EMPRESA` (administrador general de la empresa cliente) | **Empresa** | Todo lo de las cuentas de su empresa | Todas las de su empresa | Todo lo de su empresa |
| `ADMINISTRADOR` (administrador universal, Sulotec) | **Todo** | Todo | Todas | Todas las empresas |

Lo que está fuera del alcance responde **404**, como si no existiera. Editar sigue siendo solo del dueño (matrices). El rol `ADMINISTRADOR_EMPRESA` lo crea `RolesInicializador` al arrancar si no existe. **Denuncias:** la lista de receptores (`GET /api/denuncias/receptores`) solo muestra los de la propia empresa y una denuncia no puede dirigirse a un receptor de otra empresa (400); la empresa de una denuncia es la de su receptor (o, si no tiene, la de quien la registró). Las listas negativas, PEP y sancionadas son información pública de referencia y siguen siendo comunes.

`GET /api/usuarios` sigue la misma regla: un administrador ve todas las cuentas; una cuenta común, solo las de su empresa. Los clientes no deben saber unos de otros: no hay selector de empresa, iniciar sesión ya identifica a la empresa.

Cómo comprobarlo: con una cuenta común de la empresa A, llamar a la API con datos de la empresa B (`?empresa=B`, el id de un registro de B, `usuarios-por-empresa?empresa=B`, `obtener-usuario?usuario=<cuenta de B>`, crear un registro a nombre de B). Todo debe devolver los datos de A, 404 o 403; nunca los de B.
