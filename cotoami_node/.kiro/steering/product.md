# Product Overview

Cotoami Node Server is a distributed database system for collaborative knowledge management. It provides a SQLite-based database that can be accessed remotely by multiple clients, enabling real-time synchronization of changes across a network of nodes.

## Key Features

- **Distributed Architecture**: Nodes can connect as client/server with independent child/parent database relationships
- **Real-time Synchronization**: Changes propagate via WebSocket or Server-Sent Events
- **Remote Management**: Nodes can be administered remotely through owner privileges
- **Plugin System**: Extensible via WebAssembly plugins (API unstable)
- **Docker Deployment**: Containerized for easy deployment and scaling

## Core Concepts

- **Nodes**: Individual database instances that can connect to form a network
- **Cotos**: Basic content units stored in the database
- **Cotonomas**: Collections or containers for organizing Cotos
- **Client/Server vs Child/Parent**: Separation between network roles (connection initiator/recipient) and database roles (data consumer/provider)