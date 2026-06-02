FROM eclipse-temurin:17-jre-alpine

ARG JAR_FILE
ARG APP_PORT=8079

WORKDIR /app
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app

COPY --chown=app:app ${JAR_FILE} /app/app.jar

USER app
EXPOSE ${APP_PORT}

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
