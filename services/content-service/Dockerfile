# Build stage
FROM gradle:8.11.1-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle buildFatJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
