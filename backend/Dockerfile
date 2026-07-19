# Build the Quarkus application inside the image so Cloud Build and local
# Docker builds do not depend on a pre-populated target directory.
FROM maven:3.9.11-amazoncorretto-21 AS build

WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN ./mvnw --batch-mode dependency:go-offline

COPY src src
RUN ./mvnw --batch-mode package -DskipTests

FROM amazoncorretto:21
ENV LANGUAGE='en_US:en'

WORKDIR /deployments

# Copy the Quarkus fast-jar layout (built by mvn package)
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185:0 /workspace/target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080

USER 185

ENV JAVA_APP_JAR=/deployments/quarkus-run.jar

ENTRYPOINT ["sh", "-c", "java \
  -Dquarkus.http.host=0.0.0.0 \
  -Dquarkus.http.port=${PORT:-8080} \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -XX:MaxRAMPercentage=75.0 \
  ${JAVA_OPTS:-} \
  -jar ${JAVA_APP_JAR}"]
