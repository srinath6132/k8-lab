FROM eclipse-temurin:17-jre
WORKDIR /app
RUN useradd --system --uid 10001 --home-dir /nonexistent --shell /usr/sbin/nologin appuser
COPY target/devops-demo.jar /app/app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
