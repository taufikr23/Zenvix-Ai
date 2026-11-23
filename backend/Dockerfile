FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system documind && useradd --system --gid documind documind
WORKDIR /app
RUN mkdir -p /app/data/documents && chown -R documind:documind /app/data
COPY --from=build --chown=documind:documind /build/target/documind-api.jar app.jar
USER documind
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
