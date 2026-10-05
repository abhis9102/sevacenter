# SevaCenter backend image (M5). Built from clean source, ships only a JRE and the jar.
# Base images are pinned by digest: a tag can be moved to different content, a digest can't.
# Update them on purpose (Dependabot, docker ecosystem) and let the image scan judge the result.

# --- stage 1: build the jar from clean source (JDK + Maven live only here) ---
FROM eclipse-temurin:21-jdk@sha256:3e3c176ffed168beb42c607be9bc1639b466cf00261a0fb04425562c9d0c5c2b AS build
WORKDIR /src
# Dependencies first: this layer is reused until pom.xml changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline
# Then the source: a code change rebuilds only from here.
COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests

# --- stage 2: what ships. Distroless Debian 13: Java, libc, CA certificates, tzdata. No shell, no
# package manager, no curl: after a code-execution bug there is little to work with. ---
FROM gcr.io/distroless/java21-debian13:nonroot@sha256:0a1f5a75661918de9c0813f287f651c3bf2d6dd752eada5f084eb0c1f14ced9e
LABEL org.opencontainers.image.title="sevacenter-backend" \
      org.opencontainers.image.source="https://github.com/abhis9102/sevacenter"
WORKDIR /app
# Owned by root and read-only: the app can run its code but never rewrite it.
COPY --from=build --chown=root:root --chmod=0444 /src/target/backend-0.0.1-SNAPSHOT.jar /app/app.jar
# The image's built-in unprivileged user (nonroot), by number so platforms can verify it.
USER root
EXPOSE 8080
# No HEALTHCHECK: there is no shell or curl to run one. The platform (ALB / ECS) checks
# GET /actuator/health instead (an accepted risk in .trivy/accepted.toml for the Dockerfile scan, G6).
# Exec form: java is PID 1 and gets SIGTERM, so shutdown is graceful.
# MaxRAMPercentage: size the heap from the container's memory limit, not the host's.
# ExitOnOutOfMemoryError: die and get replaced, rather than limp on in a broken state.
ENTRYPOINT ["/usr/bin/java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
