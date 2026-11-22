# syntax=docker/dockerfile:1

# ---------- Build stage: Maven + JDK 21 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Resolve dependencies first so this layer is cached when only sources change.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy application sources and build the executable Spring Boot JAR.
COPY src ./src
RUN mvn -B -DskipTests package

# ---------- Runtime stage: JRE 21 only ----------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# The Spring Boot Maven plugin repackages the artifact defined in pom.xml
# (artifactId=taskflow-backend, version=1.0.0) into this executable JAR.
COPY --from=build /app/target/taskflow-backend-1.0.0.jar /app/taskflow-backend.jar

EXPOSE 8080

# The prod profile is fixed here; all secrets/config come from environment variables.
ENV SPRING_PROFILES_ACTIVE=prod

# Render injects PORT. Fall back to 8080 for local runs. `exec` keeps the JVM
# as PID 1 so SIGTERM is handled correctly on shutdown.
ENTRYPOINT ["sh", "-c", "exec java -jar /app/taskflow-backend.jar --spring.profiles.active=prod --server.address=0.0.0.0 --server.port=${PORT:-8080}"]
