# SevaCenter staff app + MandirCenter public site (M5). Built from clean source; ships only
# Next.js's standalone server. Base images pinned by digest (Dependabot bumps them; G6 judges).

# --- stage 1: dependencies, exactly the lockfile, install scripts off (npm's classic attack path) ---
FROM node:22-trixie-slim@sha256:b26b04c123d9ff8ab646ceb18b9d75a1173acf64b9a401094b906d27b29338d4 AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --ignore-scripts --no-audit --no-fund

# --- stage 2: build ---
FROM node:22-trixie-slim@sha256:b26b04c123d9ff8ab646ceb18b9d75a1173acf64b9a401094b906d27b29338d4 AS build
WORKDIR /app
ENV NEXT_TELEMETRY_DISABLED=1
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build

# --- stage 3: what ships. Distroless Node 22 on Debian 13: no shell, no package manager, no curl. ---
FROM gcr.io/distroless/nodejs22-debian13:nonroot@sha256:ec2313763dd43931543bd03830466e0c409ce73a487e8d46f10db72d3b816c1c
LABEL org.opencontainers.image.title="sevacenter-frontend" \
      org.opencontainers.image.source="https://github.com/abhis9102/sevacenter"
WORKDIR /app
ENV NODE_ENV=production NEXT_TELEMETRY_DISABLED=1 PORT=3000 HOSTNAME=0.0.0.0
# The standalone server and its static assets, owned by root and read-only to the app.
COPY --from=build --chown=root:root --chmod=0555 /app/.next/standalone ./
COPY --from=build --chown=root:root --chmod=0555 /app/.next/static ./.next/static
# The image's built-in unprivileged user (nonroot), by number so platforms can verify it.
USER 65532:65532
EXPOSE 3000
# No HEALTHCHECK (no shell or curl): the platform checks the server over HTTP. Accepted for the
# Dockerfile scan in .trivy/accepted.toml (G6).
# The distroless entrypoint is node; exec form, so node is PID 1 and shuts down on SIGTERM.
CMD ["server.js"]
