# Work log

## Bootstrap

1. Add a member `cotoami_db` to the Cargo Workspace
2. `cargo new cotoami_db --lib`
3. Create initial migrations
    1. Create a `/migrations` directory
    2. Create directories for each migration
        * Each directory's name should have the structure `{version}_{migration_name}`
        * Each directory contains two files, `up.sql` and `down.sql`
4. Add diesel-related dependencies in `Cargo.toml`
    * diesel `2.0.4`
    * diesel_migrations `2.0.0`
    * libsqlite3-sys `0.26.0`
    * dotenvy `0.15`
    * chrono `0.4.23`
5. Install Diesel CLI
    * `cargo install diesel_cli --no-default-features --features sqlite-bundled`
        * set feature `sqlite-bundled` to fix the sqlite version
6. Test migrations

    ````shell
    $ echo DATABASE_URL=./test.db > .env

    # Create a database and run any pending migrations 
    $ diesel setup

    # Run all pending migrations
    $ diesel migration run

    # Run the down.sql and then the up.sql for the most recent migration
    $ diesel migration redo
    ````

### Note

* [Getting Started - Diesel](https://diesel.rs/guides/getting-started)
* [diesel_migrations - Rust](https://docs.rs/diesel_migrations/latest/diesel_migrations/index.html)
* Not using Diesel CLI (except for testing migrations)
    * to grasp and control over what files and folders are created
    * to manually specify each migration version
        * I'd rather use serial numbers instead of timestamp.
            * timestamp is verbose and has unnecessary meaning (creation date) as a version number.
        * [The apidoc](https://docs.rs/diesel_migrations/latest/diesel_migrations/index.html) says "It is recommended that you use the timestamp of creation for the version.", but it seems there's no reason mentioned for it (perhaps, it's a safer and scalable way of naming to order migrations).
* libsqlite3-sys and bundled SQLite version
    * Diesel v2.0.4
        * libsqlite3-sys [>=0.17.2, <0.27.0](https://github.com/diesel-rs/diesel/blob/v2.0.4/diesel/Cargo.toml#L19)
        * libsqlite3-sys [0.26.0](https://github.com/rusqlite/rusqlite/blob/v0.29.0/libsqlite3-sys/Cargo.toml#L3) is the latest as of 2023-05-09
        * The SQLite version is [3.41.2](https://github.com/rusqlite/rusqlite/blob/v0.29.0/libsqlite3-sys/sqlite3/bindgen_bundled_version.rs#L3)

