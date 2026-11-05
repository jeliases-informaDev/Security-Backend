# Inicio único con la cuenta Sulotec

Las cuentas, licencias, accesos y tokens los maneja Sulotec. PLAFT se entra igual que GeoMatrix: con la cuenta Sulotec (`cuenta.sulotec.com`, Keycloak). El titular de la licencia entra la primera vez sin que nadie le cree nada a mano; los demás usuarios de su empresa los crea él dentro de PLAFT. El login con usuario y contraseña sigue igual.

El código está en `src/main/kotlin/com/security/modules/auth/sso`.

## Variables

| Variable | Para qué | Producción |
|---|---|---|
| `SSO_ISSUER` | Emisor de Keycloak. **Vacío = apagado** (nada falla, solo hay login local) | `https://cuenta.sulotec.com/realms/sulotec` |
| `SSO_CLIENT_ID` | Cliente público de Keycloak (OpenID Connect + PKCE S256) | `plaft` |
| `SSO_REDIRECT_URI` | Dirección exacta de retorno del front (se manda igual en el canje) | `https://plaft.sulotec.com/sso` |

Si falta el emisor, el cliente o la dirección de retorno, el inicio único queda apagado.

## Endpoints (públicos)

- `GET /api/auth/sso/config` → `{ "activo": false }` o `{ "activo": true, "issuer": "...", "clientId": "plaft", "redirectUri": "https://plaft.sulotec.com/sso" }`.
- `POST /api/auth/sso` con `{ "code": "...", "codeVerifier": "..." }` → 200 con **exactamente** el mismo cuerpo que `POST /api/auth/login` (el token propio de PLAFT).

El backend canjea el código en `{issuer}/protocol/openid-connect/token` (`grant_type=authorization_code`, `client_id`, `code`, `redirect_uri`, `code_verifier`) y verifica el `id_token`: firma RS256 con las llaves de `{issuer}/protocol/openid-connect/certs` (en caché 10 minutos; si llega una llave nueva se relee), emisor, audiencia = cliente y vencimiento. Del token se leen `email`, `email_verified`, `given_name`, `family_name`, `roles` (también `realm_access.roles`), `empresa_uuid` y `empresa_nombre`.

## Errores

Todos con el formato de error de siempre (`message` + `code`). Nunca 502 ni 504 (Cloudflare reemplaza esas respuestas y la persona no vería el mensaje).

| Status | code | Cuándo |
|---|---|---|
| 404 | `SSO_INACTIVO` | El inicio único está apagado en este servidor |
| 400 | `SSO_DATOS` | Falta el código o el verificador (o Sulotec no mandó el nombre de la empresa en el alta) |
| 401 | `SSO_CODIGO` | Sulotec no aceptó el código (venció o ya se usó) |
| 401 | `SSO_TOKEN` | El id_token no se pudo verificar |
| 424 | `SSO_EMISOR` | No se pudo hablar con Keycloak (o con el servicio de licencias durante el alta del titular) |
| 403 | `SSO_SIN_CORREO` | La cuenta Sulotec no tiene correo |
| 403 | `SSO_CORREO_NO_VERIFICADO` | El token dice `email_verified: false` |
| 403 | `SSO_CUENTA_INACTIVA` | El usuario de PLAFT con ese correo está desactivado |
| 403 | `SSO_SIN_PERMISO` | Doble llave: usuario `ADMINISTRADOR` en PLAFT sin `administrador-general` en Sulotec (o usuario sin rol) |
| 403 | `SSO_SIN_CUENTA` | No tiene usuario en PLAFT y no le toca alta automática |
| 403 | `SSO_SIN_LICENCIA` | Su empresa no tiene licencia de PLAFT (o las licencias están apagadas) |
| 409 | `SSO_EMPRESA_EN_CONFLICTO` | Ya existe una empresa con ese nombre pero con otro UUID |

## Quién entra

Las reglas están en `SsoReglas.decidir` (sin red ni base, con pruebas en `SsoReglasTest` y `SsoServiceTest`).

1. **Ya hay un usuario de PLAFT con ese correo** (sin distinguir mayúsculas; el correo es único):
   - inactivo → `SSO_CUENTA_INACTIVA`;
   - activo → entra con **su rol y su empresa de PLAFT** (nunca los del token). Doble llave: si su rol es `ADMINISTRADOR` (Sulotec), el token debe traer `administrador-general`; si no → `SSO_SIN_PERMISO`.
2. **No hay usuario**:
   - el token trae `administrador-general` → se crea un usuario `ADMINISTRADOR` sin empresa, igual que el administrador de Sulotec que ya existe;
   - el token trae `producto-plaft` y `empresa_uuid` → se pregunta la licencia de esa empresa al servicio de licencias **sin caché**. Si el correo es el del titular de la licencia → **alta automática** (abajo). Sin licencia o con las licencias apagadas → `SSO_SIN_LICENCIA`; si no es el titular → `SSO_SIN_CUENTA`;
   - cualquier otro caso → `SSO_SIN_CUENTA`: "Tu cuenta Sulotec todavía no tiene usuario en PLAFT. Pide al administrador de tu empresa que te cree la cuenta con este mismo correo."
3. Al entrar se emite el token de PLAFT con la misma lógica del login (`AuthService.respuestaDe`).

## Alta automática del titular

- Si no existe una empresa con ese UUID, se crea con el nombre que da la licencia (`empresa_nombre`; si no viene, el del token), ese UUID y la sigla que propone el sistema. Si ya existe una empresa con ese nombre y otro UUID → `SSO_EMPRESA_EN_CONFLICTO`: un administrador de Sulotec debe igualar el UUID (`PUT /api/empresas/{id}`).
- Se crea el usuario con rol `ADMINISTRADOR_EMPRESA` de esa empresa: usuario = sigla + parte local del correo (`hcyo.ana`; con un número al final si ya existe), nombres y apellidos del token y una contraseña aleatoria larga que nadie conoce. Entra siempre por Sulotec; si quiere clave local usa «olvidé mi contraseña».
- El titular **no pasa por el control de cupo** (el dueño de la licencia siempre puede entrar), pero después se reportan las cuentas activas a Sulotec como en cualquier alta.
- Queda en el log (`Inicio único: alta automática de ...`); PLAFT no tiene una tabla de auditoría.

El administrador universal que se crea solo tampoco pasa por el cupo (no tiene empresa).
