FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src ./src
RUN ./mvnw package -DskipTests -B

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd -r -u 1001 appuser
COPY --from=builder /app/target/*.jar app.jar
USER appuser
EXPOSE 8082

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD curl --fail --silent http://localhost:8082/actuator/health || exit 1

ARG SOURCE_DIGEST
LABEL org.logitrack.source.sha256=$SOURCE_DIGEST

ENTRYPOINT ["java", "-jar", "app.jar"]
