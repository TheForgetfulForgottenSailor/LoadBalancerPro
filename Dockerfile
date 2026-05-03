# Base images are pinned by digest. Update the tag and digest together after review.
# maven:3.9-eclipse-temurin-17
FROM maven:3.9-eclipse-temurin-17@sha256:036d1a6f2965e4368157bb87f02cd31652a96918a26f7eb5ae45a0aa33f2cb8e AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package spring-boot:repackage \
    && JAR="$(ls -t target/LoadBalancerPro-*.jar | grep -Ev '(-sources|-javadoc|-tests)\.jar$' | head -n 1)" \
    && test -n "$JAR" \
    && cp "$JAR" /workspace/app.jar

# eclipse-temurin:17-jre-jammy
FROM eclipse-temurin:17-jre-jammy@sha256:642d45bf22d3cb9face159181732ed9fa70873b2681e50445eff7d4785c176bb
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system loadbalancer \
    && useradd --system --gid loadbalancer --home-dir /app --shell /usr/sbin/nologin loadbalancer

COPY --from=build --chown=loadbalancer:loadbalancer /workspace/app.jar app.jar

USER loadbalancer:loadbalancer

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD curl -fsS -o /dev/null http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
CMD ["--server.address=0.0.0.0"]
