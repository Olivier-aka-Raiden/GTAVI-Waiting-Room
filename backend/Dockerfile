# Multi-stage Docker build for Quarkus JVM (Java 26)
# Produces a lean image (~200 MB) with the uber-jar

FROM eclipse-temurin:26-jdk-alpine AS build
WORKDIR /build

# Cache Maven deps first (layer caching)
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline -B -q || true

# Build the uber-jar
COPY src ./src
RUN ./mvnw package -DskipTests -Dquarkus.package.jar.type=uber-jar -B -q

# Runtime stage — slim JRE, no JDK
FROM eclipse-temurin:26-jre-alpine
WORKDIR /app

# Non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy the uber-jar
COPY --from=build /build/target/gtavi-backend-*-runner.jar app.jar

# Health check via Quarkus microprofile health
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD wget -qO- http://localhost:8080/q/health/live || exit 1

EXPOSE 8080

# JVM flags optimized for Cloud Run (2 GiB / 2 vCPU)
# -XX:MaxRAMPercentage=75.0 → leave headroom for container overhead
# -XX:+UseZGC → low-latency GC suitable for serverless
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+UseZGC", \
  "-XX:+ZGenerational", \
  "-Djava.awt.headless=true", \
  "-jar", "app.jar"]
