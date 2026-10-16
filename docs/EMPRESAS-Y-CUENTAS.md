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
- `GET /api/usuarios` lo puede llamar cualquier cuenta autenticada y devuelve las cuentas de todas las empresas (nombre, correo, empresa y rol). Conviene restringirlo o filtrarlo por empresa antes de tener varios clientes.
