FROM eclipse-temurin:21-jdk AS build

WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && for i in 1 2 3 4 5; do ./mvnw -q -DskipTests dependency:go-offline && break || sleep 5; done

COPY src src
RUN for i in 1 2 3 4 5; do ./mvnw -q -DskipTests package && break || sleep 5; done

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends wget ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system betai \
    && useradd --system --gid betai --home-dir /app betai
COPY --from=build /workspace/target/bet-ai-0.1.0-SNAPSHOT.jar /app/bet-ai.jar
RUN chown -R betai:betai /app

USER betai
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/bet-ai.jar"]
