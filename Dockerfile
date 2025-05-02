# Dockerfile for Cotoami Node Server (cotoami_node) 
# based on https://docs.docker.com/guides/rust/develop/

################################################################################
# Create a stage for building the application.

ARG RUST_VERSION=1.85.0
ARG APP_NAME=cotoami_node

FROM rust:${RUST_VERSION}-slim-bookworm AS build

ARG APP_NAME
WORKDIR /app

# Copy the source files needed to build cotoami_node.
#
# Bind mount, which is used in the original example, won't work in some
# environments (such as Podman: https://github.com/containers/podman/issues/15423).
COPY Cargo.toml Cargo.lock ./
COPY cotoami_db ./cotoami_db
COPY cotoami_node ./cotoami_node
# cotoami_desktop is not needed, but it cannot be simply excluded from the workspace
# (Option to ignore missing workspace members when building: 
# https://github.com/rust-lang/cargo/issues/14566)
# so let's include minimal files required to build.
COPY cotoami_desktop/tauri/Cargo.toml ./cotoami_desktop/tauri/
COPY cotoami_desktop/tauri/src/main.rs ./cotoami_desktop/tauri/src/

# Build the application.
#
# Leverage a cache mount to /usr/local/cargo/registry/
# for downloaded dependencies and a cache mount to /app/target/ for
# compiled dependencies which will speed up subsequent builds.
#
# Leverage a bind mount to the src directory to avoid having to copy the
# source code into the container. Once built, copy the executable to an
# output directory before the cache mounted /app/target is unmounted.
RUN --mount=type=cache,target=/app/target/ \
    --mount=type=cache,target=/usr/local/cargo/registry/ \
    <<EOF
set -e
cargo build --package $APP_NAME --locked --release
cp ./target/release/$APP_NAME /bin/server
EOF

################################################################################
# Create a new stage for running the application that contains the minimal
# runtime dependencies for the application. This often uses a different base
# image from the build stage where the necessary files are copied from the build
# stage.
FROM debian:bookworm-slim AS final

# Create a non-privileged user that the app will run under.
# See https://docs.docker.com/develop/develop-images/dockerfile_best-practices/
ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser
USER appuser

# Copy the executable from the "build" stage.
COPY --from=build /bin/server /bin/

# Expose the port that the application listens on.
# 5103 is the default number, which can be change via COTOAMI_SERVER_PORT
EXPOSE 5103

# What the container should run when it is started.
CMD ["/bin/server"]
