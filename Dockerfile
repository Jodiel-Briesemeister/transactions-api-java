FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are resolved from the pom alone first, so a source-only change
# does not invalidate the cached dependency layer.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Runs unprivileged: nothing in the app needs root.
RUN addgroup -S spring && adduser -S spring -G spring
COPY --from=build /build/target/*.jar app.jar
USER spring

EXPOSE 8080

# Honour container memory limits instead of assuming the host's RAM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
