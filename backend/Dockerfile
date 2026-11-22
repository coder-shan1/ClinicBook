# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S clinicbook && adduser -S clinicbook -G clinicbook
COPY --from=build --chown=clinicbook:clinicbook /app/target/clinicbook-backend-*.jar /app/app.jar

# Production profile by default: Neon PostgreSQL + secrets from the environment.
# docker-compose overrides this with SPRING_PROFILES_ACTIVE=local.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

USER clinicbook
EXPOSE 8080
STOPSIGNAL SIGTERM

# Render injects PORT; SERVER_PORT is still honoured for other platforms.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://127.0.0.1:${PORT:-${SERVER_PORT:-8080}}/actuator/health" || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
