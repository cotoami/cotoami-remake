# Dockerfile for Cotoami Node Server (cotoami_node) 

ARG RUST_VERSION=1.85.0
ARG TARGETARCH=arm64

################################################################################
# Stage to build an application binary
FROM clux/muslrust:${TARGETARCH}-${RUST_VERSION}-stable-2025-03-18 AS build

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
RUN --mount=type=cache,target=/app/target/ \
    --mount=type=cache,target=/usr/local/cargo/registry/ \
    <<EOF
set -e
cargo build --package cotoami_node --locked --release
ls ./target/
cp ./target/aarch64-unknown-linux-musl/release/cotoami_node /cotoami_node
EOF


################################################################################
# Stage to build a docker image
FROM gcr.io/distroless/static

# Copy the executable from the "build" stage.
COPY --from=build /cotoami_node /

# Expose the port that the application listens on.
# 5103 is the default number, which can be change via COTOAMI_SERVER_PORT
EXPOSE 5103

# What the container should run when it is started.
CMD ["/cotoami_node"]
