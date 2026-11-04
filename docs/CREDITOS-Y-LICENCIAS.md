# Sistema de Asignación y Consumo de Créditos/Tokens (BPMN P1 - P4)

Este documento describe la arquitectura de **consumo y recepción de créditos** en este producto (`Security-Backend`), alineado con el ecosistema **Sulotec Central**.

---

## 1. Responsabilidades: Sulotec Central vs Este Producto

| Responsabilidad | Sulotec Central (SaaS Principal) | Este Producto (`Security-Backend`) |
|---|:---:|:---:|
| Planes comerciales y precios | ✅ Sí | ❌ No |
| Cobranza, facturación y pagos | ✅ Sí | ❌ No |
| Modificación de tarifas por token | ✅ Sí | ❌ No |
| Asignación/sincronización de bolsa contratada | ✅ Envía asignación | ✅ Recibe y aplica (`POST /api/licencias/sincronizar`) |
| Consumo automático en los 5 módulos | ❌ No | ✅ Sí (descuento en BD por cada acción) |
| Bloqueo al agotar créditos (HTTP 402) | ❌ No | ✅ Sí |
| Consulta de saldo para la empresa cliente | ❌ No | ✅ Sí (`GET /api/licencias/mi-bolsa`) |

---

## 2. Modelo de Bolsa Única y Tolerancia (BPMN P3)

- **Bolsa de la Empresa**: Los créditos/tokens pertenecen a la empresa cliente (`licencias_empresa`). Todas las cuentas de la empresa consumen de la misma bolsa.
- **Tolerancia de Exceso (10%)**: Si los créditos contratados se agotan, el sistema permite seguir operando dentro del porcentaje de tolerancia configurado (por defecto 10%). Los consumos se marcan con `exceso = true`.
- **Bloqueo por Saldo Insuficiente**: Si se supera la bolsa más la tolerancia, cualquier operación que requiera créditos se rechaza con **HTTP 402 (`PAYMENT_REQUIRED`)** (`SaldoInsuficienteException`).
- **Exención del Administrador Universal**: Cuentas con rol `ADMINISTRADOR` (Sulotec) están exentas de consumo de créditos.
- **Aviso al 80%**: Al alcanzar o superar el 80% de consumo de la bolsa, se genera un aviso interno (`avisos_licencia`) y se notifica al `ADMINISTRADOR_EMPRESA`.

---

## 3. Cupo de Usuarios Activos (BPMN P2)

- El plan contratado define un cupo máximo de usuarios (`cupo_usuarios`).
- El rol `ADMINISTRADOR_EMPRESA` puede registrar usuarios (`POST /api/usuarios`) para su propia empresa (`USUARIO`, `SUPERVISOR`, `ADMINISTRADOR_EMPRESA`).
- Si se alcanza el cupo de usuarios activos, la creación se rechaza con **HTTP 409 (`CONFLICT`)** (`CupoUsuariosExcedidoException`).
- Desactivar un usuario libera cupo automáticamente.

---

## 4. Vigencia y Vencimiento de Licencia (BPMN P4)

- **7 días antes del vencimiento**: Se emite aviso al administrador de empresa para gestionar la renovación o ampliación con Sulotec.
- **Vencimiento alcanzado (7 días de gracia en Solo Lectura)**: Las consultas gratuitas siguen disponibles; las acciones que consumen créditos o crean registros se bloquean con **HTTP 403 (`FORBIDDEN`)** (`LicenciaVencidaException`).
- **Pasados los 7 días de gracia**: La licencia pasa a estado `SUSPENDIDA`.

---

## 5. Tarifas de Consumo en los 5 Módulos

| Módulo | Acción (`AccionCredito`) | Costo | Descripción / Regla |
|---|---|:---:|---|
| **Listas Negativas** | `LISTA_BUSQUEDA` | 1 | Cobro únicamente en la primera página (`page = 0`). La paginación adicional y el historial no descuentan. |
| **Listas Negativas** | `LISTA_PDF` | 1 | Descarga oficial de reporte de coincidencia / ficha en PDF (`GET /api/listas-negativas/{id}/pdf`). |
| **Scoring** | `SCORING_EVALUAR` | 2 | Por cada evaluación ejecutada y guardada. |
| **Matrices** | `MATRIZ_REGISTRAR` | 3 | Por registrar formalmente el análisis (estado `ABIERTO`). Los borradores no cobran. |
| **Matrices** | `MATRIZ_PDF` | 1 | Por cada exportación de matriz a PDF. |
| **Denuncias** | `DENUNCIA_CREAR` | 2 | Por registro de denuncia (incluso anónima con usuario logueado). |
| **Denuncias** | `DENUNCIA_DOCUMENTOS` | 1 | Por lote de archivos subidos. |
| **Operaciones** | `OPERACION_REGISTRAR` | 2 | Por inserción de una operación. |
| **Operaciones** | `OPERACION_PDF` | 1 | Por cada exportación a PDF de una operación. |
| **Consultas y Edición** | — | 0 | Historiales, listados, catálogos, vistas previas y actualizaciones no consumen créditos. |

---

## 6. Endpoints Disponibles en este Backend

### Licencias y Bolsa (`/api/licencias`)
- `GET /api/licencias/mi-bolsa`: Devuelve el saldo de créditos, porcentaje consumido, días restantes y estado de la empresa del usuario autenticado.
- `GET /api/licencias/movimientos`: Historial paginado de consumos y recargas de la empresa.
- `GET /api/licencias/avisos`: Notificaciones internas (80% consumo, exceso de tolerancia, proximidad de vencimiento).
- `PUT /api/licencias/avisos/{id}/leido`: Marca un aviso como leído.
- `POST /api/licencias/sincronizar`: **Solo para Sulotec Central (Rol `ADMINISTRADOR`)**. Recibe la asignación de créditos, cupo de usuarios y vencimiento tras una compra/recarga en Sulotec.
- `GET /api/licencias/empresas/{empresaId}`: Consulta el estado de licencia de cualquier empresa (solo `ADMINISTRADOR`).
