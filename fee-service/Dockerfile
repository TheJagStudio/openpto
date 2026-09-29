# syntax=docker/dockerfile:1
# Build context: the fee-service folder.   docker build -t openpto/fee-service .

# ---- build: compile + boot jar (tests run in CI, not in the image build) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY legacy-fee-monolith/build.gradle legacy-fee-monolith/build.gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
COPY legacy-fee-monolith/src legacy-fee-monolith/src
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/fee-service-*.jar extract --layers --launcher --destination /workspace/extracted

# ---- runtime: JRE only, non-root, layered for cache-friendly pushes ----
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER app
EXPOSE 8082
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    DB_URL="jdbc:postgresql://host.docker.internal:5433/openpto"
# Health: point the ECS/ALB target-group check at /actuator/health/readiness.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
