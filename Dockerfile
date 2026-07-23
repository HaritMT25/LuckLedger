# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Stage 1 — build the luckledger-app fat jar with the full reactor.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Copy the POMs first so the (slow) dependency download layer is cached and only
# re-runs when a pom.xml actually changes — not on every source edit.
COPY pom.xml ./
COPY luckledger-domain/pom.xml        luckledger-domain/
COPY luckledger-pool/pom.xml          luckledger-pool/
COPY luckledger-mechanic/pom.xml      luckledger-mechanic/
COPY luckledger-generation/pom.xml    luckledger-generation/
COPY luckledger-distribution/pom.xml  luckledger-distribution/
COPY luckledger-player/pom.xml        luckledger-player/
COPY luckledger-scratch-flow/pom.xml  luckledger-scratch-flow/
COPY luckledger-api/pom.xml           luckledger-api/
COPY luckledger-cli/pom.xml           luckledger-cli/
COPY luckledger-app/pom.xml           luckledger-app/

# Warm the local Maven repo for the luckledger-app build graph. Best-effort: a
# clean tree can't fully resolve every reactor goal offline, so don't fail the
# build here — the packaging step below still has network if it needs it.
RUN mvn -B -e -pl luckledger-app -am dependency:go-offline || true

# Now bring in the sources and build only the app module plus what it needs.
COPY luckledger-domain/src        luckledger-domain/src
COPY luckledger-pool/src          luckledger-pool/src
COPY luckledger-mechanic/src      luckledger-mechanic/src
COPY luckledger-generation/src    luckledger-generation/src
COPY luckledger-distribution/src  luckledger-distribution/src
COPY luckledger-player/src        luckledger-player/src
COPY luckledger-scratch-flow/src  luckledger-scratch-flow/src
COPY luckledger-api/src           luckledger-api/src
COPY luckledger-cli/src           luckledger-cli/src
COPY luckledger-app/src           luckledger-app/src

RUN mvn -B -DskipTests -pl luckledger-app -am package \
    && cp luckledger-app/target/luckledger-app-*.jar /workspace/app.jar

# ---------------------------------------------------------------------------
# Stage 2 — minimal JRE runtime, non-root.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as an unprivileged user rather than root.
RUN groupadd --system luckledger && useradd --system --gid luckledger --home /app luckledger
COPY --from=build /workspace/app.jar /app/app.jar
USER luckledger

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
