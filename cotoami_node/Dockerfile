# Dockerfile to build a multi-arch image of Cotoami Node Server (cotoami_node) 

ARG RUST_VERSION=1.88.0


################################################################################
# Build an application binary
FROM --platform=$BUILDPLATFORM rust:${RUST_VERSION}-bookworm AS build

ARG TARGETPLATFORM
ARG BUILDARCH

# Determine the target triple according to TARGETPLATFORM.
#
# We don't use musl because there seems to be a performance issue:
# https://andygrove.io/2020/05/why-musl-extremely-slow/
RUN case "${TARGETPLATFORM}" in \
    "linux/amd64") echo x86_64-unknown-linux-gnu > /target_triple ;; \
    "linux/arm64") echo aarch64-unknown-linux-gnu > /target_triple ;; \
    *) exit 1 ;; \
    esac

# Convert BUILDARCH into the other format.
RUN case "${BUILDARCH}" in \
    "amd64") echo x86_64 > /build_arch ;; \
    "arm64") echo aarch64 > /build_arch ;; \
    *) exit 1 ;; \
    esac

# Install Zig needed by cargo-zigbuild.
# https://zig.guide/getting-started/installation/
RUN mkdir -p /opt/zig && \
    curl -L https://ziglang.org/download/0.14.0/zig-linux-$(cat /build_arch)-0.14.0.tar.xz \
    | tar -xJf - --strip-components=1 -C /opt/zig
ENV PATH="/opt/zig:$PATH"

WORKDIR /app

# Copy the source files needed to build cotoami_node.
COPY Cargo.toml Cargo.lock ./
COPY crates ./crates

# Build the application.
#
# Leverage a cache mount to /usr/local/cargo/registry/
# for downloaded dependencies and a cache mount to /app/target/ for
# compiled dependencies which will speed up subsequent builds.
# https://github.com/moby/buildkit/blob/v0.21.1/frontend/dockerfile/docs/reference.md#run---mounttypecache
RUN --mount=type=cache,sharing=locked,target=/usr/local/cargo/registry/ \
    --mount=type=cache,sharing=locked,target=/app/target/ \
    <<EOF
set -ex
cargo install --locked cargo-zigbuild
rustup target add $(cat /target_triple)
cargo zigbuild --package cotoami_node --locked --release --target $(cat /target_triple)
cp ./target/$(cat /target_triple)/release/cotoami_node /
EOF


################################################################################
# Build a production image
FROM --platform=$TARGETPLATFORM debian:bookworm-slim

# Create a non-privileged user that the app will run under.
# (also create a home directory for the default wasmtime cache `~/.cache/wasmtime`).
ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/cotoami" \
    --shell "/sbin/nologin" \
    --uid "${UID}" \
    appuser
USER appuser

# Copy the executable from the "build" stage.
COPY --from=build /cotoami_node /cotoami/node

# Expose the port that the application listens on.
# 5103 is the default number, which can be change via COTOAMI_SERVER_PORT.
EXPOSE 5103

# What the container should run when it is started.
CMD ["/cotoami/node"]
