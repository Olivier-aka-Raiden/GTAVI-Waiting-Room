# Single-stage build — expects pre-built Quarkus app in target/quarkus-app/
# Run before docker build:  ./mvnw package -DskipTests
#
# For gcloud run deploy --source . :
#   gcloud run deploy gtavi-api --source . --region europe-west1 ...

FROM amazoncorretto:26
ENV LANGUAGE='en_US:en'

WORKDIR /deployments

# Copy the Quarkus fast-jar layout (built by mvn package)
COPY --chown=185:0 target/quarkus-app/lib/   /deployments/lib/
COPY --chown=185:0 target/quarkus-app/*.jar  /deployments/
COPY --chown=185:0 target/quarkus-app/app/   /deployments/app/
COPY --chown=185:0 target/quarkus-app/quarkus/ /deployments/quarkus/

EXPOSE 8080

USER 185

ENV JAVA_APP_JAR=/deployments/quarkus-run.jar

ENTRYPOINT ["sh", "-c", "java \
  --enable-preview \
  -Dquarkus.http.host=0.0.0.0 \
  -Dquarkus.http.port=${PORT:-8080} \
  -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
  -XX:MaxRAMPercentage=75.0 \
  ${JAVA_OPTS:-} \
  -jar ${JAVA_APP_JAR}"]
