FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

COPY app.jar /app/app.jar

EXPOSE 8080 8443

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
  CMD curl -fkSs "${HEALTHCHECK_URL:-http://localhost:8080/actuator/health}" || exit 1

ENTRYPOINT ["/entrypoint.sh"]
