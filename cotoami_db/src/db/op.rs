//! Framework of composable database operations

use anyhow::Result;
use derive_new::new;
use diesel::connection::{AnsiTransactionManager, TransactionManager};
use diesel::sqlite::SqliteConnection;
use std::ops::{Deref, DerefMut};

/// A runnable unit of database operation
pub trait Operation<Conn, T> {
    /// Runs this operation with a `Context`
    fn run(&self, ctx: &mut Context<'_, Conn>) -> Result<T>;
}

/// A `Context` is practically a database connection needed to run an `Operation`.
///
/// It doesn't have public constructors so that a client of this module has to use
/// the functions such as `run` or `run_in_transaction` in this module to invoke an `Operation`.
pub struct Context<'a, Conn: 'a> {
    conn: &'a mut Conn,
}

impl<'a, Conn> Context<'a, Conn> {
    /// Private constructor
    fn new(conn: &'a mut Conn) -> Self {
        Context { conn }
    }

    pub fn conn(&mut self) -> &mut Conn {
        self.conn
    }
}

/////////////////////////////////////////////////////////////////////////////
// Composite Operation
/////////////////////////////////////////////////////////////////////////////

pub struct CompositeOp<F> {
    f: F,
}

impl<Conn, T, F> Operation<Conn, T> for CompositeOp<F>
where
    F: Fn(&mut Context<'_, Conn>) -> Result<T>,
{
    fn run(&self, ctx: &mut Context<'_, Conn>) -> Result<T> {
        (self.f)(ctx)
    }
}

pub fn composite_op<Conn, F, T>(f: F) -> CompositeOp<F>
where
    F: Fn(&mut Context<'_, Conn>) -> Result<T>,
{
    CompositeOp { f }
}

/////////////////////////////////////////////////////////////////////////////
// WritableConnection
/////////////////////////////////////////////////////////////////////////////

#[derive(new)]
pub struct WritableConnection(SqliteConnection);

impl Deref for WritableConnection {
    type Target = SqliteConnection;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl DerefMut for WritableConnection {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl WritableConnection {
    // Copying and pasting the functions from `diesel::sqlite::SqliteConnection` as a workaround
    // for the following issue:

    // It is not possible to use `immediate_transaction` of the inner connection because it will
    // cause mutable borrowing twice in one call.

    /// Runs a transaction with `BEGIN IMMEDIATE`
    ///
    /// Same implementation as `diesel::sqlite::SqliteConnection`:
    /// <https://github.com/diesel-rs/diesel/blob/v2.1.0/diesel/src/sqlite/connection/mod.rs#L248-L254>
    pub fn immediate_transaction<T, E, F>(&mut self, f: F) -> Result<T, E>
    where
        F: FnOnce(&mut Self) -> Result<T, E>,
        E: From<diesel::result::Error>,
    {
        self.transaction_sql(f, "BEGIN IMMEDIATE")
    }

    /// Runs `f` as a transaction activated by `sql`
    ///
    /// Same implementation as `diesel::sqlite::SqliteConnection`:
    /// <https://github.com/diesel-rs/diesel/blob/v2.1.0/diesel/src/sqlite/connection/mod.rs#L285-L301>
    fn transaction_sql<T, E, F>(&mut self, f: F, sql: &str) -> Result<T, E>
    where
        F: FnOnce(&mut Self) -> Result<T, E>,
        E: From<diesel::result::Error>,
    {
        AnsiTransactionManager::begin_transaction_sql(self.deref_mut(), sql)?;
        match f(&mut *self) {
            Ok(value) => {
                AnsiTransactionManager::commit_transaction(self.deref_mut())?;
                Ok(value)
            }
            Err(e) => {
                AnsiTransactionManager::rollback_transaction(self.deref_mut())?;
                Err(e)
            }
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// Read Operation
/////////////////////////////////////////////////////////////////////////////

pub struct ReadOp<F> {
    f: F,
}

impl<T, F> Operation<SqliteConnection, T> for ReadOp<F>
where
    F: Fn(&mut SqliteConnection) -> Result<T>,
{
    fn run(&self, ctx: &mut Context<'_, SqliteConnection>) -> Result<T> {
        (self.f)(ctx.conn())
    }
}

impl<T, F> Operation<WritableConnection, T> for ReadOp<F>
where
    F: Fn(&mut SqliteConnection) -> Result<T>,
{
    fn run(&self, ctx: &mut Context<'_, WritableConnection>) -> Result<T> {
        (self.f)(ctx.conn())
    }
}

// Workaround to implement trait alias until trait_alias is fully introduced
// https://doc.rust-lang.org/beta/unstable-book/language-features/trait-alias.html
pub trait ReadOperation<T>:
    Operation<SqliteConnection, T> + Operation<WritableConnection, T>
{
}

impl<T, F> ReadOperation<T> for ReadOp<F> where F: Fn(&mut SqliteConnection) -> Result<T> {}

pub fn read_op<T, F>(f: F) -> ReadOp<F>
where
    F: Fn(&mut SqliteConnection) -> Result<T>,
{
    ReadOp { f }
}

pub fn run<Op, T>(conn: &mut SqliteConnection, op: Op) -> Result<T>
where
    Op: Operation<SqliteConnection, T>,
{
    op.run(&mut Context::new(conn))
}

/////////////////////////////////////////////////////////////////////////////
// Write Operation
/////////////////////////////////////////////////////////////////////////////

pub struct WriteOp<F> {
    f: F,
}

impl<T, F> Operation<WritableConnection, T> for WriteOp<F>
where
    F: Fn(&mut WritableConnection) -> Result<T>,
{
    fn run(&self, ctx: &mut Context<'_, WritableConnection>) -> Result<T> {
        (self.f)(ctx.conn())
    }
}

pub fn write_op<T, F>(f: F) -> WriteOp<F>
where
    F: Fn(&mut WritableConnection) -> Result<T>,
{
    WriteOp { f }
}

pub fn run_in_transaction<Op, T>(conn: &mut WritableConnection, op: Op) -> Result<T>
where
    Op: Operation<WritableConnection, T>,
{
    conn.immediate_transaction(|conn| op.run(&mut Context::new(conn)))
}
