# Cotoami Database

This package provides the API for the Cotoami Database, which is built on top of SQLite.

* It uses [Diesel](https://diesel.rs/) for accessing the SQLite database.
* Since `async` is not supported, when performing database operations (typically through `DatabaseSession` method calls), be sure to use `tokio::task::spawn_blocking` or a similar approach.
* Only one `Database` instance can access a given database file at a time (exclusive access is managed via `Database::lock_file`).
* The database schema is defined in SQL files under the `migrations` directory and in Rust in `src/schema.rs`.

```rust
let root_dir = tempdir()?;
let db = Database::new(&root_dir)?;
let ds = db.new_session()?;
let ((local_node, node), changelog) = ds.init_as_node(Some("My Node"), Some("owner-password"))?;
```


## Migrating Data from the Original Cotoami

Cotoami Database provides a tool for converting export data from the original Cotoami (*.json) into a database file (SQLite).

To use this tool, you need to have both Git and Rust installed in your environment:

* [Git \- Downloads](https://git-scm.com/downloads)
* [Install Rust \- Rust Programming Language](https://www.rust-lang.org/tools/install)

First, clone the source code of Cotoami Remake using the following command:

```shell
git clone https://github.com/cotoami/cotoami-remake.git
```

Then move into the following directory:

```shell
cd cotoami-remake/cotoami_db
```

Run the conversion with this command:

```shell
cargo run --example import -- /path/to/cotoami-export.json /path/to/database-folder "Database Name"
```

* User information from the original Cotoami export data will not be carried over. All Cotos will be imported into the new database without user attribution.
* You can use the `--exclude-cotonoma <cotonoma ID>` option to exclude a specific Cotonoma and all Cotos belonging to it. This option can be repeated to exclude multiple Cotonoma entries.


