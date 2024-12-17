# Build stage
FROM maven:3.8.6-eclipse-temurin-17 AS build
ENV HOME=/usr/app
WORKDIR $HOME

# Copy the pom.xml and resolve dependencies
COPY pom.xml $HOME

# Use cache for Maven dependencies to speed up subsequent builds
RUN --mount=type=cache,target=/root/.m2 mvn dependency:resolve

# Copy the rest of the project files and build the application, skipping tests
COPY . $HOME
RUN --mount=type=cache,target=/root/.m2 mvn clean install -DskipTests

# Package stage
FROM eclipse-temurin:17-jre-jammy
ARG JAR_FILE=/usr/app/target/*.jar
COPY --from=build $JAR_FILE /app/runner.jar
EXPOSE 4000
ENTRYPOINT ["java", "-jar", "/app/runner.jar"]
