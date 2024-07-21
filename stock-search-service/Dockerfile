FROM openjdk:17-jdk-slim
ARG JAR_FILE
VOLUME /tmp
COPY ${JAR_FILE} app.jar
LABEL authors="akra-dev"
ENTRYPOINT ["java","-jar","/app.jar"]