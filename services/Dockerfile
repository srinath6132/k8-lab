ARG SERVICE

FROM maven:3.9-eclipse-temurin-17 AS build
ARG SERVICE
WORKDIR /workspace
COPY services/ services/
RUN mvn -B -f services/pom.xml -pl "${SERVICE}" -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 --home-dir /nonexistent --shell /usr/sbin/nologin appuser
ARG SERVICE
COPY --from=build --chown=10001:10001 /workspace/services/${SERVICE}/target/${SERVICE}.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
