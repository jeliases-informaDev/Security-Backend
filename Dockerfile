# Imagen del backend para desarrollo local con Docker (la usa docker-compose.yml de este repo).
# No hace falta tener JDK ni Gradle instalados: el build se hace aqui dentro.

# ---- Etapa 1: compilar ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# Limites de memoria para que compile en laptops de 8 GB (Gradle y Kotlin en un solo proceso).
ENV GRADLE_OPTS="-Xmx1g"

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Si el archivo se clono en Windows con saltos de linea CRLF, el script no corre en Linux.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

COPY src src

# La cache de Gradle se conserva entre builds: la primera vez descarga dependencias, las siguientes son rapidas.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon -Pkotlin.compiler.execution.strategy=in-process bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain' | head -n 1)" /app/app.jar

# ---- Etapa 2: ejecutar ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/app.jar app.jar
USER app

EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
