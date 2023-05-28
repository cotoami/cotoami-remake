# Notes on cotoami_db

## Considerations in SQLite's ROWID as a primary key

* Currently, each entity table has a `rowid` as a primary key and `uuid` as a unique column, and each foreign key refers to the `uuid` column in the target table rather than the primary key.

### Diesel joinable macro

```
diesel::joinable!(pages -> books (book_id));
```

```
let page_with_book = pages::table
    .inner_join(books::table)
    .filter(books::title.eq("Momo"))
    .select((Page::as_select(), Book::as_select()))
    .load::<(Page, Book)>(conn)?;

println!("Page-Book pairs: {page_with_book:?}");
```

* <https://diesel.rs/guides/relations.html>
* [`joinable` macro](https://docs.rs/diesel/latest/diesel/macro.joinable.html)
    * Allow two tables to be referenced in a join query without providing an explicit ON clause.
    * In order to use joinable, the foreign key has to refer to **the primary key of the parent table**.
        * The generated ON clause will always join to the primary key of the parent table.
* `QueryDsl::inner_join()`
    * The `ON` clause of the join statement can be inferred based on the `joinable!` call in your `schema.rs` file.
    * It’s possible to specify custom `ON` clauses via `JoinDsl::on`.
        * <https://docs.diesel.rs/2.1.x/diesel/query_dsl/trait.JoinOnDsl.html#method.on>

There is a couple of ways to modify the current schema to follow the Diesel's rule (the foreign key has to refer to the primary key of the parent table).

1. Changing the references of foreign keys to `rowid`
    * This loses the universality of the data since rowids are node-local. Imported data cannot be stored in a local db without modifying the foreign keys.
2. Deleting the explicit `rowid` and make the `uuid` as a primary key
    * This would lose the performance merit of ROWID.
        * ROWID is still present as an implict column, but defining it in `schema.rs` and `#[diesel(primary_key(rowid))]` is a little bit awkward and doesn't look a good practice.
    * There are cases where a serial number as a primary key is handy.
        * For example, `nodes` table has a "self node row", which is flagged by `rowid = 1`.
    * If the rowid is not aliased by INTEGER PRIMARY KEY then it is not persistent and might change.
    * How about `WITHOUT ROWID`?

### `WITHOUT ROWID`

* [Clustered Indexes and the WITHOUT ROWID Optimization](https://www.sqlite.org/withoutrowid.html)
    * In some cases, a WITHOUT ROWID table can use about half the amount of disk space and can operate nearly twice as fast.
    * There can often be space and performance advantages to using WITHOUT ROWID on tables that have non-integer or composite PRIMARY KEYs.
    * However...
        * Ordinary rowid tables will run faster with a single INTEGER PRIMARY KEY.
        * WITHOUT ROWID tables work best when individual rows are not too large. 
            * A good rule-of-thumb is that the average size of a single row in a WITHOUT ROWID table should be less than about 1/20th the size of a database page (about 200 bytes each for 4KiB page size).
