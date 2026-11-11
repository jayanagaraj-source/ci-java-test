# syntax=docker/dockerfile:1

# ---- Build stage: JDK + Gradle wrapper ------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached across source changes.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon -q dependencies > /dev/null

COPY src ./src
# Tests run in CI / locally (./gradlew build); the image build only packages.
RUN ./gradlew --no-daemon -q bootJar \
 && java -Djarmode=tools -jar build/libs/task-api-*.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: JRE only, non-root ------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app

# Spring Boot layers, least to most frequently changing.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER 10001:10001
ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/actuator/health/liveness" > /dev/null || exit 1

# Exec form so SIGTERM reaches the JVM and triggers graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
