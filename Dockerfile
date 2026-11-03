# syntax=docker/dockerfile:1
#
# The Docker image of GPlay Promo Code Provider: the Ktor server plus the compiled web app.
# See README.md ("Deployment") for how the image is built, run and deployed to Fly.io.
#
# Two stages ("multi-stage build"): the first has everything needed to compile (JDK, Gradle,
# Node.js), the second only what is needed to run (a Java runtime and the compiled files). Only the
# second stage ends up in the image, so it stays small and contains no build tools.

# ---- Stage 1: build ----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src

# Node.js (downloaded by Gradle for the web build) needs libatomic, which the slim base image lacks.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libatomic1 \
    && rm -rf /var/lib/apt/lists/*

# The sources (.dockerignore keeps the host's build outputs, caches and databases out).
COPY . .

# Compiles the server (:server:installDist puts it with its libraries and a start script into
# server/build/install/server) and the web app (Wasm, with the JS build as fallback for browsers
# without the Wasm features it needs, into webApp/build/dist/composeWebCompatibility/productionExecutable).
# Tests run in CI, not here. The cache mounts keep Gradle's and Kotlin/JS's downloads between
# builds on the same machine; they are not part of the image.
RUN --mount=type=cache,target=/root/.gradle \
    --mount=type=cache,target=/root/.cache \
    ./gradlew --no-daemon --console=plain \
        -Pkotlin.compiler.execution.strategy=in-process \
        :server:installDist :webApp:composeCompatibilityBrowserDistribution

# ---- Stage 2: runtime --------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-noble

# sqlite3: only for backups and manual checks (`fly ssh console`), see README. The server itself
# brings its own SQLite in the JDBC driver.
RUN apt-get update \
    && apt-get install -y --no-install-recommends sqlite3 \
    && rm -rf /var/lib/apt/lists/*

# The server runs as this unprivileged user, not as root.
RUN groupadd --system app && useradd --system --gid app --home-dir /app --no-create-home app

WORKDIR /app
COPY --from=build /src/server/build/install/server /app
COPY --from=build /src/webApp/build/dist/composeWebCompatibility/productionExecutable /app/web
COPY --chmod=755 docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh

# The SQLite file lives in /data. In production a volume is mounted there, so it survives a new
# image (redeploy); without a volume, Docker creates an anonymous one.
RUN mkdir /data && chown app:app /data
VOLUME /data

# Configuration (see ServerConfig.kt). ADMIN_USERNAME and ADMIN_PASSWORD have no default: they are
# secrets and are passed when the container starts.
# JAVA_OPTS: the JVM may use up to 60% of the container's memory for its heap; the rest is left for
# the JVM itself (threads, classes, SQLite). On OutOfMemoryError it stops, so the platform restarts it.
ENV PORT=8080 \
    DATABASE_PATH=/data/gplaypromocodeprovider.db \
    WEB_DIR=/app/web \
    JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["/app/bin/server"]
