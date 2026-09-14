#  ComplyTools - Core Backend

Este repositorio contiene la API principal del ecosistema ComplyTools. Está construido con **Kotlin y Spring Boot**, siguiendo una Arquitectura Hexagonal.

---

## ALTO: Requisitos Previos (Instalaciones necesarias)
A diferencia de los repositorios de Frontend, este backend **NO** usa Node.js ni `npm`. Para que este proyecto funcione en tu computadora, debes tener instalado lo siguiente:

1. **Java JDK 17:** Descarga e instala [Amazon Corretto 17] o [Eclipse Temurin 17].
2. **MySQL 8.0+:** Necesitas el motor de base de datos corriendo en tu máquina.
3. **IntelliJ IDEA (Community o Ultimate):** Es el IDE recomendado y obligatorio para trabajar cómodamente con Kotlin.

---

##  Paso a paso para levantar el proyecto en tu PC

### Paso 1: Clonar el proyecto
Abre tu terminal y descarga el código:

```bash
git clone <URL_DEL_REPO>
cd security-backend

Configuracion credenciales MySQL

spring.datasource.url=jdbc:mysql://localhost:3306/complytools_db?useSSL=false&serverTimezone=UTC
# Cambia esto por tu usuario de MySQL (suele ser "root")
spring.datasource.username=TU_USUARIO 
# Cambia esto por tu contraseña de MySQL
spring.datasource.password=TU_CONTRASEÑA
spring.jpa.hibernate.ddl-auto=update

## Dependencias

Levantar el servidor
No necesitas instalar dependencias manualmente. El sistema lo hará solo. Abre la terminal integrada de IntelliJ (o tu terminal normal dentro de la carpeta del proyecto) y ejecuta:

Si usas Windows:

DOS
.\gradlew bootRun
Si usas Mac / Linux:

Bash
./gradlew bootRun


⏳ Nota: La primera vez que ejecutes este comando tardará un poco, ya que Gradle descargará automáticamente todas las librerías necesarias de Spring Boot y Kotlin.

✅ ¿Cómo sé que funcionó?
Si todo está correcto, en la consola dejarán de aparecer letras cargando y verás un mensaje similar a:
Started SecurityBackendApplication in X seconds.
El servidor estará listo y escuchando peticiones en http://localhost:8081.
