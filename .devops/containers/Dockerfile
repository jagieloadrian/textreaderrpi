# syntax=docker/dockerfile:1

FROM gradle:8.11.1-jdk25 AS build
WORKDIR /workspace

COPY . .
RUN gradle --no-daemon shadowJar

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system textreaderrpi \
    && useradd --system --gid textreaderrpi --home /opt/textreaderrpi --shell /usr/sbin/nologin textreaderrpi

WORKDIR /opt/textreaderrpi
COPY --from=build /workspace/build/libs/TextReaderRpi-all.jar /opt/textreaderrpi/TextReaderRpi-all.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS http://localhost:8080/health/ready || exit 1

USER textreaderrpi
ENTRYPOINT ["java", "-jar", "/opt/textreaderrpi/TextReaderRpi-all.jar"]

