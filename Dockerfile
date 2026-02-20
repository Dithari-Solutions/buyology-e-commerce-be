FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/*.jar built.jar

EXPOSE 8080

ENTRYPOINT ["java","-jar","built.jar"]