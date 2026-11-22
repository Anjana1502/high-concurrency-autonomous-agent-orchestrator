# Member 4 - multi-stage JDK 21 build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY pom.xml ./
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src src
COPY proto proto
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

RUN groupadd -r orchestrator && useradd -r -g orchestrator orchestrator
COPY --from=build /workspace/target/*.jar app.jar
RUN chown -R orchestrator:orchestrator /app
USER orchestrator

ENV JAVA_OPTS="--enable-preview -XX:+UseSerialGC -XX:MaxRAMPercentage=70"
EXPOSE 8080 9090

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
