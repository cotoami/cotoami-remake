use std::ops::DerefMut;

use anyhow::{anyhow, Result};
use cotoami_db::{
    db::{
        op,
        op::{AsReadableConn, Context, Operation, WritableConn},
    },
    prelude::*,
};
use derive_new::new;
use diesel::prelude::*;
use schema::test;

pub mod common;

#[test]
fn operation() -> Result<()> {
    // setup
    let db_file = common::temp_file_path()?;
    let mut conn = Database::create_rw_conn(&Database::to_file_uri(&db_file)?)?;
    create_table(&mut conn)?;

    // when: successful transaction
    op::run_in_transaction(&mut conn, move |ctx: &mut Context<'_, WritableConn>| {
        insert("Cloud").run(ctx)?;
        insert("Aerith").run(ctx)?;
        Ok(())
    })?;

    // then
    let rows = op::run(conn.deref_mut(), all())?;
    assert_eq!(into_values(&rows), vec!["Cloud", "Aerith"]);

    // when: failing transaction
    let result = op::run_in_transaction(&mut conn, move |ctx: &mut Context<'_, WritableConn>| {
        insert("Tifa").run(ctx)?;
        Err(anyhow!("An error occurred during a transaction."))?;
        insert("Barret").run(ctx)?;
        Ok(())
    });

    // then
    let rows = op::run(conn.deref_mut(), all())?;
    assert_eq!(into_values(&rows), vec!["Cloud", "Aerith"]);
    assert!(result.is_err());

    // when: map
    let mapped_op = all().map(|rows: Vec<TestRow>| {
        rows.iter()
            .map(|row| format!("Hello, {}!", &row.value))
            .collect::<Vec<_>>()
    });

    // then
    let values = op::run(conn.deref_mut(), mapped_op)?;
    let values = Vec::from_iter(values.iter().map(String::as_str));
    assert_eq!(values, vec!["Hello, Cloud!", "Hello, Aerith!"]);

    // when: and_then
    let and_then_op = get(1).and_then(|row: Option<TestRow>| {
        insert(format!("{} is a protagonist.", row.unwrap().value))
    });
    op::run_in_transaction(&mut conn, and_then_op)?;

    // then
    let rows = op::run(conn.deref_mut(), all())?;
    assert_eq!(
        into_values(&rows),
        vec!["Cloud", "Aerith", "Cloud is a protagonist."]
    );

    Ok(())
}

const TABLE_DDL: &str = r#"
    CREATE TABLE test (
        rowid INTEGER NOT NULL PRIMARY KEY,
        value TEXT NOT NULL
    )
"#;

mod schema {
    diesel::table! {
        test (rowid) {
            rowid -> BigInt,
            value -> Text,
        }
    }
}

#[derive(Insertable, new)]
#[diesel(table_name = test)]
pub struct NewTestRow {
    value: String,
}

#[derive(Debug, Clone, PartialEq, Eq, Queryable)]
pub struct TestRow {
    pub rowid: i64,
    pub value: String,
}

fn create_table(conn: &mut WritableConn) -> Result<()> {
    diesel::sql_query(TABLE_DDL).execute(conn.deref_mut())?;
    Ok(())
}

fn insert<T: Into<String>>(value: T) -> impl Operation<WritableConn, TestRow> {
    let new_row = NewTestRow::new(value.into());
    op::write_op(move |conn| {
        diesel::insert_into(test::table)
            .values(&new_row)
            .get_result(conn.deref_mut())
            .map_err(anyhow::Error::from)
    })
}

fn get<Conn: AsReadableConn>(rowid: i64) -> impl Operation<Conn, Option<TestRow>> {
    op::read_op(move |conn| {
        test::table
            .find(rowid)
            .first(conn)
            .optional()
            .map_err(anyhow::Error::from)
    })
}

fn all() -> impl Operation<SqliteConnection, Vec<TestRow>> {
    op::read_op(move |conn| {
        test::table
            .order(test::rowid.asc())
            .load::<TestRow>(conn)
            .map_err(anyhow::Error::from)
    })
}

fn into_values<'a>(rows: &'a Vec<TestRow>) -> Vec<&'a String> {
    rows.iter().map(|row| &row.value).collect::<Vec<_>>()
}
