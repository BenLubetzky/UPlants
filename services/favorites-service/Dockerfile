# Build stage
FROM gradle:9.8.2-jdk21 AS build
WORKDIR /app
COPY . .
RUN gradle buildFatJar --no-daemon

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*-all.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
