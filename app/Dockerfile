
FROM eclipse-temurin:17-jre

WORKDIR /app

# Update OS packages and security patches
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create non-root application user
RUN useradd --system --uid 10001 \
    --home-dir /nonexistent \
    --shell /usr/sbin/nologin appuser

COPY --chown=10001:10001 target/devops-demo.jar /app/app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75.0","-jar","/app/app.jar"]
