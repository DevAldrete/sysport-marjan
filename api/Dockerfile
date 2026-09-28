# Build core + api (Java 21 / Micronaut) and package the executable jar.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

COPY pom.xml ./
COPY core/pom.xml core/pom.xml
COPY api/pom.xml api/pom.xml
RUN mvn -q -B -pl core,api -am -DskipTests dependency:go-offline || true

COPY core core
COPY api api
RUN mvn -q -B -pl core,api -am package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/api/target/sysport-api-1.0-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
