# syntax=docker/dockerfile:1.7
# ---- build stage: compile, test-free boot jar, split into layers -------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY transform-lambda/build.gradle transform-lambda/build.gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null
COPY transform-lambda/src transform-lambda/src
COPY samples samples
COPY src src
# add -Paws to ship the AWS SDK for the S3ObjectStorage adapter (profile "aws")
ARG GRADLE_ARGS=""
RUN ./gradlew --no-daemon bootJar ${GRADLE_ARGS} \
 && java -Djarmode=tools -jar build/libs/ingest-service-*.jar extract --layers --launcher --destination /extracted

# ---- runtime stage: JRE only, non-root ---------------------------------------------------------
FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 openpto \
 && useradd --system --uid 10001 --gid openpto --home-dir /app --shell /usr/sbin/nologin openpto \
 && mkdir -p /app /data/buckets && chown -R openpto:openpto /app /data
WORKDIR /app
COPY --from=build --chown=openpto:openpto /extracted/dependencies/ ./
COPY --from=build --chown=openpto:openpto /extracted/spring-boot-loader/ ./
COPY --from=build --chown=openpto:openpto /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=openpto:openpto /extracted/application/ ./
USER openpto
ENV PORT=8083 \
    STORAGE_ROOT=/data/buckets \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME ["/data/buckets"]
EXPOSE 8083
# Orchestrators should probe /actuator/health/liveness and /actuator/health/readiness
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]