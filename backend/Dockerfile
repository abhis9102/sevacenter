# SevaCenter backend image (M5). Built from clean source, ships only a JRE and the jar.
# Base images are pinned by digest: a tag can be moved to different content, a digest can't.
# Update them on purpose (Dependabot, docker ecosystem) and let the image scan judge the result.

# --- stage 1: build the jar from clean source (JDK + Maven live only here) ---
FROM eclipse-temurin:21-jdk@sha256:23cc5734ff46e776f51ce66eb9ee08444fd47caabec0a95b7eb6e94be2d3696d AS build
WORKDIR /src
# unzip: without it the Maven wrapper silently fetches the .tar.gz instead of the .zip, whose
# SHA-256 isn't the one pinned in maven-wrapper.properties, and refuses it. Build stage only.
RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*
# Dependencies first: this layer is reused until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
# Then the source: a code change rebuilds only from here.
COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests

# --- stage 2: what ships. Distroless Debian 13: Java, libc, CA certificates, tzdata. No shell, no
# package manager, no curl: after a code-execution bug there is little to work with. ---
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:f3ac871fb5802c2739bb23b67212e67f341cbe754131528d2e9849072f9efb9a
LABEL org.opencontainers.image.title="sevacenter-backend" \
      org.opencontainers.image.source="https://github.com/abhis9102/sevacenter"
WORKDIR /app
# Owned by root and read-only: the app can run its code but never rewrite it.
COPY --from=build --chown=root:root --chmod=0444 /src/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
# The image's built-in unprivileged user (nonroot), by number so platforms can verify it.
USER 65532:65532
EXPOSE 8080
# No HEALTHCHECK: there is no shell or curl to run one. The platform (ALB / ECS) checks
# GET /actuator/health instead (an accepted risk in .trivy/accepted.toml for the Dockerfile scan, G6).
# Exec form: java is PID 1 and gets SIGTERM, so shutdown is graceful.
# MaxRAMPercentage: size the heap from the container's memory limit, not the host's.
# ExitOnOutOfMemoryError: die and get replaced, rather than limp on in a broken state.
ENTRYPOINT ["/usr/bin/java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
