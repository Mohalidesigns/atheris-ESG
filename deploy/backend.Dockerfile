# WP-54: Spring Boot backend image. Build from the repository root:
#   docker build -f deploy/backend.Dockerfile -t atheris-esg-backend .
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY esg-backend/.mvn esg-backend/.mvn
COPY esg-backend/mvnw esg-backend/pom.xml esg-backend/
RUN cd esg-backend && ./mvnw -B -q dependency:go-offline
COPY esg-backend esg-backend
RUN cd esg-backend && ./mvnw -B -q package -DskipTests -Dcheckstyle.skip -Djacoco.skip \
    && cp target/esg-platform-*-SNAPSHOT.jar /src/app.jar

FROM eclipse-temurin:25-jre
RUN groupadd --system esg && useradd --system --gid esg --home /app esg
WORKDIR /app
COPY --from=build /src/app.jar /app/app.jar
USER esg
EXPOSE 8080
# Secrets come from the environment (ProductionSecretsPolicy refuses development values under prod/staging).
ENV SPRING_PROFILES_ACTIVE=prod JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
