# Work log

## Bootstrap

1. Add a member `cotoami_db` to the Cargo Workspace
2. `cargo new cotoami_db --lib`
3. Create initial migrations
    1. Create a `/migrations` directory
    2. Create directories for each migration
        * Each directory's name should have the structure `{version}_{migration_name}`
        * Each directory contains two files, `up.sql` and `down.sql`

### Note

* [Getting Started - Diesel](https://diesel.rs/guides/getting-started)
* [diesel_migrations - Rust](https://docs.rs/diesel_migrations/latest/diesel_migrations/index.html)
* Not using Diesel CLI (except for testing migrations)
    * to grasp and control over what files and folders are created
    * to manually specify each migration version
        * I'd rather use serial numbers instead of timestamp.
            * timestamp is verbose and has unnecessary meaning (creation date) as a version number.
        * [The apidoc](https://docs.rs/diesel_migrations/latest/diesel_migrations/index.html) says "It is recommended that you use the timestamp of creation for the version.", but it seems there's no reason mentioned for it (perhaps, it's a safer and scalable way of naming to order migrations).

