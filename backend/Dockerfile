# UrbanTwin API — Spring Boot 3 / Java 17 (contract §10)
# Multi-stage: Maven build → slim JRE runtime image.

# Stage 1: build the fat jar
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Copy the build descriptor first for better layer caching
COPY pom.xml .

# Copy sources and package (tests run in CI, not in the image build)
COPY src ./src
RUN mvn -B -DskipTests package

# Stage 2: runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S urbantwin && adduser -S urbantwin -G urbantwin
USER urbantwin

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
