# Notes on cotoami_db::models

## `ToSql` implementation

* When possible, implementations of this trait should prefer using an existing implementation, rather than writing to out directly.
    * Changes in Diesel 2.0: 
        * <https://diesel.rs/guides/migration_guide.html#changed-tosql-implementations>
        * "This has the implication that for generic implementations using a inner existing `ToSql` implementation you cannot create temporary values anymore and forward them to the inner implementation."

## Derivable Traits in Diesel

### [Identifiable](https://docs.diesel.rs/2.0.x/diesel/prelude/trait.Identifiable.html)

* It represents a single row in a database table.
* It assumes that the table name is the plural `snake_case` form of the struct name.
* By default, the primary key field is assumed to be a single field called `id`.
    * This project uses `rowid` in most cases instead of `id`, so we put the following attribute on `Identifiable` structs:
        * `#[diesel(primary_key(rowid))]`
* It must be implemented to use associations. 
* It allows you to pass the struct to `update`.
    * When we write `update(post)`, that’s equivalent to writing `update(posts.find(post.id))`, or `update(posts.filter(id.eq(post.id)))`.
    * <https://diesel.rs/guides/all-about-updates.html>

### [Selectable](https://docs.diesel.rs/2.0.x/diesel/expression/trait.Selectable.html)

* Types which implement Selectable represent the select clause of a SQL query. 
    * `SelectableHelper::as_select()` to construct the select clause
* It assumes that the table name is the plural `snake_case` form of the struct name.
* It assumes that every field name matches the name of the corresponding column in `schema.rs`

### Deserialization

#### [Queryable](https://docs.diesel.rs/2.0.x/diesel/prelude/trait.Queryable.html)

* It represents the result of a SQL query.
* It assumes that all fields on your struct matches all fields in the query, including the order and count. This means that field order is significant. **Field name has no effect**.
    * It assumes that the order of fields on this struct matches the columns of the table in `schema.rs`.

#### [FromSqlRow](https://docs.diesel.rs/2.0.x/diesel/deserialize/trait.FromSqlRow.html)

* Any types which implement [FromSql](https://docs.diesel.rs/2.0.x/diesel/deserialize/trait.FromSql.html) should also implement this trait. 
* Implements `Queryable` for primitive types.

### Serialization

### [Insertable](https://docs.diesel.rs/2.0.x/diesel/prelude/trait.Insertable.html)

* It represents that a structure can be used to insert a new row into the database. 
* It assumes that the table name is the plural `snake_case` form of the struct name.
* It assumes that every field name matches the name of the corresponding column in `schema.rs`

#### [AsChangeset](https://docs.diesel.rs/2.0.x/diesel/query_builder/trait.AsChangeset.html)

* It can be passed to [update.set](https://docs.diesel.rs/2.0.x/diesel/query_builder/struct.UpdateStatement.html#method.set).
* It assumes that the table name is the plural `snake_case` form of the struct name.
* It assumes that every field name matches the name of the corresponding column in `schema.rs`
* By default, any `Option` fields on the struct are skipped if their value is `None`.
* <https://diesel.rs/guides/all-about-updates.html#aschangeset>

### [AsExpression](https://docs.diesel.rs/2.0.x/diesel/expression/trait.AsExpression.html)

* Any types which implement [ToSql](https://docs.diesel.rs/2.0.x/diesel/serialize/trait.ToSql.html) should also `#[derive(AsExpression)]`.
* A type to be used as a field in a `Insertable` or `AsChangeset` struct should implement this trait.
