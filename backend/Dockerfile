# Build stage: compile and package the Spring Boot jar with the Maven wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build

# Copy only the build files first so the dependency download is cached
# until pom.xml changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q -DskipTests package

# Run stage: a small JRE image that runs as a non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home app \
    && mkdir -p /app/data \
    && chown app /app/data

COPY --from=build /build/target/*.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
