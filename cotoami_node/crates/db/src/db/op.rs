//! Framework of composable database operations
//!
//! The main purpose of this module is to separate the responsibilities of database
//! operation into two phases: defining the content of an operation and
//! running it (as a transaction). With this separation, we can reuse and combine
//! operations without worrying about a unit of transaction, which can be decided
//! afterwards safely thanks to the types.

use std::ops::{Deref, DerefMut};

use and_then::*;
use anyhow::Result;
use derive_new::new;
use diesel::{
    connection::{AnsiTransactionManager, TransactionManager},
    sqlite::SqliteConnection,
    Connection,
};

use self::map::*;

pub mod and_then;
pub mod map;

/// A runnable unit of database operation.
#[must_use]
pub trait Operation<Conn, T> {
    /// Runs this operation with a [Context].
    fn run(self, ctx: &mut Context<'_, Conn>) -> Result<T>;

    /// Maps an `Operation<Conn, T>` to `Operation<Conn, U>` by applying a function.
    fn map<U, F>(self, f: F) -> MappedOp<Self, T, F>
    where
        F: FnOnce(T) -> U,
        Self: Sized,
    {
        map(self, f)
    }

    /// Creates a chain with another operation that depends on the result of this operation.
    fn and_then<Op, U, F>(self, f: F) -> AndThenOp<Self, T, F>
    where
        F: FnOnce(T) -> Op,
        Op: Operation<Conn, U>,
        Self: Sized,
    {
        and_then(self, f)
    }
}

/// A `Context` holds a database connection needed to run an [Operation].
///
/// Since it doesn't have public constructors, a client of this module has to use
/// the functions such as [run_read()] or [run_write()] to invoke an [Operation].
pub struct Context<'a, Conn> {
    conn: &'a mut Conn,
}

impl<'a, Conn> Context<'a, Conn> {
    /// Private constructor.
    fn new(conn: &'a mut Conn) -> Self { Context { conn } }

    pub fn conn(&mut self) -> &mut Conn { self.conn }
}

/////////////////////////////////////////////////////////////////////////////
// Composite Operation
/////////////////////////////////////////////////////////////////////////////

pub struct CompositeOp<F> {
    f: F,
}

impl<Conn, T, F> Operation<Conn, T> for CompositeOp<F>
where
    F: FnOnce(&mut Context<'_, Conn>) -> Result<T>,
{
    fn run(self, ctx: &mut Context<'_, Conn>) -> Result<T> { (self.f)(ctx) }
}

/// Defines a composite operation sharing a single [Context].
pub fn composite_op<Conn, F, T>(f: F) -> CompositeOp<F>
where
    F: FnOnce(&mut Context<'_, Conn>) -> Result<T>,
{
    CompositeOp { f }
}

/////////////////////////////////////////////////////////////////////////////
// WriteConn
/////////////////////////////////////////////////////////////////////////////

#[derive(new)]
pub struct WriteConn(SqliteConnection);

impl Deref for WriteConn {
    type Target = SqliteConnection;

    fn deref(&self) -> &Self::Target { &self.0 }
}

impl DerefMut for WriteConn {
    fn deref_mut(&mut self) -> &mut Self::Target { &mut self.0 }
}

/// The following functions is copied-and-pasted from [SqliteConnection].
/// It doesn't seem to be possible to call [SqliteConnection::immediate_transaction()] of
/// the inner connection because it will cause mutable borrowing twice in one call.
impl WriteConn {
    /// Runs a transaction with `BEGIN IMMEDIATE`.
    ///
    /// Same implementation as [SqliteConnection]:
    /// <https://github.com/diesel-rs/diesel/blob/v2.1.0/diesel/src/sqlite/connection/mod.rs#L248-L254>
    pub fn immediate_transaction<T, E, F>(&mut self, f: F) -> Result<T, E>
    where
        F: FnOnce(&mut Self) -> Result<T, E>,
        E: From<diesel::result::Error>,
    {
        self.transaction_sql(f, "BEGIN IMMEDIATE")
    }

    /// Runs `f` as a transaction activated by `sql`.
    ///
    /// Same implementation as [SqliteConnection]:
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

pub trait ReadConn {
    fn read(&mut self) -> &mut SqliteConnection;
}

/// Any SqliteConnection can be used for read operations.
impl ReadConn for SqliteConnection {
    fn read(&mut self) -> &mut SqliteConnection { self }
}

/// Any WriteConn can be used for read operations.
impl ReadConn for WriteConn {
    fn read(&mut self) -> &mut SqliteConnection { &mut self.0 }
}

pub struct ReadOp<F> {
    f: F,
}

impl<Conn, T, F> Operation<Conn, T> for ReadOp<F>
where
    Conn: ReadConn,
    F: FnOnce(&mut SqliteConnection) -> Result<T>,
{
    fn run(self, ctx: &mut Context<'_, Conn>) -> Result<T> { (self.f)(ctx.conn().read()) }
}

/// Defines a read-only operation using a raw [SqliteConnection].
pub fn read_op<T, F>(f: F) -> ReadOp<F>
where
    F: FnOnce(&mut SqliteConnection) -> Result<T>,
{
    ReadOp { f }
}

/// Runs a read operation.
///
/// The operation will be run in a `DEFERRED` transaction, which is the only way to explicitly
/// start a read transaction in SQLite (precisely, a transaction will be started by the
/// first `SELECT` statement after `BEGIN DEFERRED`).
///
/// Snapshot Isolation:
/// **In WAL mode**, SQLite exhibits "snapshot isolation". When a read transaction starts,
/// that reader continues to see an unchanging "snapshot" of the database file as it existed
/// at the moment in time when the read transaction started. Any write transactions that
/// commit while the read transaction is active are still invisible to the read transaction,
/// because the reader is seeing a snapshot of database file from a prior moment in time.
/// - <https://www.sqlite.org/isolation.html>
///
/// A write operation can be run in a `DEFERRED` transaction, but it could cause an
/// `SQLITE_BUSY_SNAPSHOT` error (<https://www.sqlite.org/rescode.html#busy_snapshot>),
/// therefore, this function should be used only for read operations.
///
pub fn run_read<Op, T>(conn: &mut SqliteConnection, op: Op) -> Result<T>
where
    Op: Operation<SqliteConnection, T>,
{
    conn.transaction(|conn| op.run(&mut Context::new(conn)))
}

/////////////////////////////////////////////////////////////////////////////
// Write Operation
/////////////////////////////////////////////////////////////////////////////

pub struct WriteOp<F> {
    f: F,
}

impl<T, F> Operation<WriteConn, T> for WriteOp<F>
where
    F: FnOnce(&mut WriteConn) -> Result<T>,
{
    fn run(self, ctx: &mut Context<'_, WriteConn>) -> Result<T> { (self.f)(ctx.conn()) }
}

/// Defines a read/write operation using a [WriteConn].
pub fn write_op<T, F>(f: F) -> WriteOp<F>
where
    F: FnOnce(&mut WriteConn) -> Result<T>,
{
    WriteOp { f }
}

/// Runs a read/write operation.
///
/// The operation will be run in a `IMMEDIATE` transaction, which immediately
/// starts a write transaction and prevents other database connections from writing
/// the database.
pub fn run_write<Op, T>(conn: &mut WriteConn, op: Op) -> Result<T>
where
    Op: Operation<WriteConn, T>,
{
    conn.immediate_transaction(|conn| op.run(&mut Context::new(conn)))
}
