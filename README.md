# Security - Core Backend

API principal del ecosistema Security. Construida con **Kotlin y Spring Boot**, siguiendo una arquitectura hexagonal modular.

- Puerto `8081` · Documentación de la API: http://localhost:8081/swagger-ui.html · Salud: http://localhost:8081/actuator/health
- Base de datos: **MySQL** (`security_db`).
- Habla con el microservicio de ML (repositorio **Security-MachineLearning**) para Indira y los casos en prensa. Es opcional: sin el ML el backend funciona igual y esas pantallas responden "servicio no disponible".

---

## 🚀 Levantarlo (sin instalar Java ni MySQL)

**Necesitas únicamente:** [Docker Desktop](https://www.docker.com/products/docker-desktop/) encendido y Git.

```bash
git clone https://github.com/jeliases-informaDev/Security-Backend.git
cd Security-Backend
docker compose up -d --build
```

La primera vez tarda ~3–5 minutos (compila el proyecto dentro de Docker); después arranca en segundos. Cuando `docker compose ps` muestre el backend como **healthy**:

| Qué | Dónde |
|---|---|
| API / Swagger | http://localhost:8081/swagger-ui.html |
| Visor de la base de datos (phpMyAdmin) | http://localhost:8082 (servidor `mysql-db`, usuario `root`, clave `root`) |
| **Usuario para entrar** | `superadmin` / `Admin12345!` (se crea solo en tu base local) |

Comandos útiles:

| Quiero… | Comando |
|---|---|
| Ver logs | `docker compose logs -f backend` |
| Apagar (los datos se conservan) | `docker compose down` |
| Empezar de cero (borra la base local) | `docker compose down -v` |
| Aplicar cambios de código | `docker compose up -d --build backend` |

> Tu base de datos es **local y propia**: lo que hagas no afecta a nadie del equipo.

## 🛠️ Programar con IntelliJ (recarga rápida)

Requisitos: **JDK 17** (Temurin o Corretto) e IntelliJ IDEA.

1. Levanta solo la base de datos: `docker compose up -d mysql-db`
2. Copia `.env.example` como `.env` en esta carpeta. Ya apunta a esa MySQL (`localhost:3307`). **No hace falta pedirle claves a nadie.**
3. Ejecuta `SecurityBackendApplication` desde IntelliJ (o `.\gradlew.bat bootRun` con `JAVA_HOME` en el JDK 17).
4. Listo cuando veas `Started SecurityBackendApplicationKt`. Entra con `superadmin` / `Admin12345!`.

## 🗄️ Ver la base de datos y hacer consultas

Todo está explicado en **[docs/BASE-DE-DATOS.md](docs/BASE-DE-DATOS.md)** (qué base es, cómo conectarte, mapa de tablas) y las consultas listas para ejecutar están en **[docs/consultas.sql](docs/consultas.sql)**. Acceso rápido: phpMyAdmin en http://localhost:8082 (servidor `mysql-db`, `root` / `root`).

## 🤖 Con Indira y casos en prensa (opcional)

Levanta el repositorio **Security-MachineLearning** (`docker compose up -d --build` en su carpeta). Publica el puerto `8000` y usa la misma clave interna por defecto, así que se conectan solos. Si cambias el puerto del ML, define `ML_PORT` (ver `.env.example`).

## ⚙️ Configuración (todo opcional)

Los valores por defecto funcionan. Para cambiarlos copia `.env.example` como `.env` (no se sube a git).

| Variable | Para qué | Por defecto |
|---|---|---|
| `MYSQL_PORT`, `BACKEND_PORT`, `PHPMYADMIN_PORT` | Puertos en tu PC si alguno está ocupado | `3307`, `8081`, `8082` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | Conexión a MySQL (al correr desde IntelliJ) | `localhost:3307/security_db`, `root` |
| `ML_BASE_URL`, `ML_API_KEY`, `ML_PORT` | Microservicio de ML | `http://localhost:8000`, `dev-internal-key-change-me` |
| `JWT_SECRET` | Firma de los tokens (mín. 32 caracteres) | clave fija solo de desarrollo |
| `CORS_ALLOWED_ORIGINS` | Origen(es) del frontend, separados por coma | `http://localhost:3000` |
| `APP_SEED_ENABLED` | Crea el usuario `superadmin`, los roles y los catálogos (incluidos los de **scoring**) en una base **nueva** | `true` en el compose |

**No actives `APP_SEED_ENABLED` contra una base compartida o de producción.** El login exige usuario y clave de **8 a 12 caracteres**.

## 🔧 Problemas comunes

| Síntoma | Solución |
|---|---|
| `port is already allocated` / puerto ocupado | Cambia el puerto en `.env` (`MYSQL_PORT=3308`, `BACKEND_PORT=8083`…) y vuelve a `docker compose up -d`. |
| El build se queda sin memoria | Docker Desktop → Settings → Resources → 4 GB o más. |
| Quiero una base limpia | `docker compose down -v` y luego `docker compose up -d --build`. |
| `Cannot connect to the Docker daemon` | Abre Docker Desktop y espera a que diga *Engine running*. |

## 🧪 Pruebas

```bash
.\gradlew.bat test
```
`SecurityBackendApplicationTests` necesita la base de datos arriba (`docker compose up -d mysql-db` y el `.env`).

## 🤝 Flujo de trabajo del equipo (Git Flow)
Nunca trabajes directo en `main`: `git checkout -b feature/mi-tarea`, commits, `git push origin feature/mi-tarea` y abre un Pull Request.
