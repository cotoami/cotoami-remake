# Project Structure

## Workspace Organization

This is a Cargo workspace with three main crates under `crates/`:

### `crates/db/` - Database Layer
- **Purpose**: SQLite database abstraction and models
- **Key modules**:
  - `src/db/`: Core database operations and transactions
  - `src/models/`: Data models and schema definitions
  - `src/db/ops/`: Database operation implementations
  - `src/db/transactions/`: Transaction management
  - `migrations/`: Diesel database migrations
  - `examples/`: Usage examples (import, search, graph)
  - `tests/`: Integration tests

### `crates/node/` - Server Implementation
- **Purpose**: HTTP/WebSocket server and node networking
- **Key modules**:
  - `src/state/`: Node state management and services
  - `src/web/`: HTTP handlers and WebSocket endpoints
  - `src/client/`: Client connection handling
  - `src/event/`: Event system (local/remote)
  - `src/service/`: Business logic services
  - `tests/`: Integration tests

### `crates/plugin_api/` - Plugin System
- **Purpose**: WebAssembly plugin API (unstable)
- **Key modules**:
  - `src/event.rs`: Plugin event definitions
  - `src/models.rs`: Plugin data models

## Code Organization Patterns

### Database Layer (`crates/db/`)
- **Operations**: `src/db/ops/` - Pure database operations
- **Transactions**: `src/db/transactions/` - Business logic transactions
- **Models**: `src/models/` - Data structures with role-based organization
- **Node Roles**: Separate modules for `client`, `server`, `parent`, `child`, `local`

### Server Layer (`crates/node/`)
- **State Management**: Centralized in `src/state/` with internal/service separation
- **Web Layer**: Clean separation between data handlers and WebSocket logic
- **Client Connections**: Retry logic and connection management
- **Service Layer**: Business logic abstracted from web concerns

## File Naming Conventions

- **Tests**: `*_test.rs` suffix for integration tests
- **Operations**: `*_ops.rs` for database operations
- **Models**: Role-based naming (e.g., `client.rs`, `server.rs`, `local.rs`)
- **Modules**: Use `mod.rs` for module organization

## Configuration Files

- `Cargo.toml`: Workspace configuration with shared dependencies
- `diesel.toml`: Database schema generation config
- `Dockerfile`: Multi-arch container build
- `compose-example.yaml`: Docker Compose example