# Technology Stack

## Core Technologies

- **Language**: Rust (minimum version 1.88)
- **Database**: SQLite with Diesel ORM
- **Web Framework**: Axum for HTTP/WebSocket server
- **Async Runtime**: Tokio
- **Serialization**: Serde (JSON), MessagePack (rmp-serde 1.3.0 - fixed version)
- **Plugin System**: Extism WebAssembly runtime
- **Containerization**: Docker with multi-arch support

## Key Dependencies

- **diesel**: SQLite ORM with migrations support
- **axum**: Web framework with WebSocket support
- **tokio**: Async runtime
- **reqwest**: HTTP client with rustls-tls
- **uuid**: UUID generation (v4, v7)
- **chrono**: Date/time handling
- **tracing**: Structured logging
- **parking_lot**: High-performance synchronization primitives

## Build System

This is a Cargo workspace with three crates:
- `crates/db`: Database layer and models
- `crates/node`: Server implementation
- `crates/plugin_api`: Plugin development API

## Common Commands

```bash
# Build all crates
cargo build

# Run tests
cargo test

# Run the node server
cargo run --package cotoami_node

# Run database examples
cargo run --example import --package cotoami_db

# Build Docker image
docker build -t cotoami-node .

# Run with Docker Compose
docker-compose -f compose-example.yaml up -d
```

## Database Management

- Uses Diesel migrations in `crates/db/migrations/`
- Schema auto-generated in `src/schema-generated.rs`
- Exclusive file locking prevents concurrent access
- All database operations should use `tokio::task::spawn_blocking`