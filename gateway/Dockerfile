# syntax=docker/dockerfile:1
# Build: docker build -t openpto/gateway ./gateway
# Run:   docker run -p 8080:8080 -e ODP_URL=http://odp:8081 -e FEE_URL=http://fees:8082 \
#          -e INGEST_URL=http://ingest:8083 -e INTERNAL_TOKEN=... -e JWKS_URI=http://odp:8081/.well-known/jwks.json openpto/gateway

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
 && java -Djarmode=tools -jar build/libs/gateway-*-SNAPSHOT.jar extract --layers --launcher --destination build/extracted

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --home-dir /app --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/build/extracted/application/ ./
USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djdk.httpclient.allowRestrictedHeaders=host"
# ECS/ALB health check: GET /actuator/health/readiness (liveness: /actuator/health/liveness)
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
