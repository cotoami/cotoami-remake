use anyhow::Result;
use diesel::connection::{AnsiTransactionManager, TransactionManager};
use diesel::sqlite::SqliteConnection;
use std::ops::{Deref, DerefMut};

pub trait Operation<Conn, T> {
    fn run<'a, 'b>(&self, ctx: &'a mut Context<'b, Conn>) -> Result<T>;
}

pub struct Context<'a, Conn: 'a> {
    conn: &'a mut Conn,
}

// TODO: why do I have to delare two lifetime parameters here?
impl<'a, 'b, Conn> Context<'b, Conn> {
    // private constructor
    fn new(conn: &'b mut Conn) -> Self {
        Context { conn: conn }
    }

    fn conn(&'a mut self) -> &'a mut Conn {
        self.conn
    }
}

//
// Composite Operation
//

pub struct CompositeOp<F> {
    f: F,
}

impl<Conn, T, F> Operation<Conn, T> for CompositeOp<F>
where
    for<'a, 'b> F: Fn(&'a mut Context<'b, Conn>) -> Result<T>,
{
    fn run<'a, 'b>(&self, ctx: &'a mut Context<'b, Conn>) -> Result<T> {
        (self.f)(ctx)
    }
}

pub fn composite_op<Conn, F, T>(f: F) -> CompositeOp<F>
where
    for<'a, 'b> F: Fn(&'a mut Context<'b, Conn>) -> Result<T>,
{
    CompositeOp { f }
}

//
// Read Operation
//

pub struct ReadOp<F> {
    f: F,
}

impl<T, F> Operation<SqliteConnection, T> for ReadOp<F>
where
    F: Fn(&mut SqliteConnection) -> Result<T>,
{
    fn run<'a, 'b>(&self, ctx: &'a mut Context<'b, SqliteConnection>) -> Result<T> {
        (self.f)(ctx.conn())
    }
}

pub fn read_op<T, F>(f: F) -> ReadOp<F>
where
    F: Fn(&mut SqliteConnection) -> Result<T>,
{
    ReadOp { f }
}

pub fn run<'a, Op, T>(conn: &'a mut SqliteConnection, op: Op) -> Result<T>
where
    Op: Operation<SqliteConnection, T>,
{
    op.run(&mut Context::new(conn))
}

//
// WritableConnection
//

pub struct WritableConnection(pub SqliteConnection);

impl<'a> Deref for WritableConnection {
    type Target = SqliteConnection;

    fn deref(&self) -> &Self::Target {
        &self.0
    }
}

impl<'a> DerefMut for WritableConnection {
    fn deref_mut(&mut self) -> &mut Self::Target {
        &mut self.0
    }
}

impl WritableConnection {
    pub fn immediate_transaction<T, E, F>(&mut self, f: F) -> Result<T, E>
    where
        F: FnOnce(&mut Self) -> Result<T, E>,
        E: From<diesel::result::Error>,
    {
        self.transaction_sql(f, "BEGIN IMMEDIATE")
    }

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

impl<T, F> Operation<WritableConnection, T> for ReadOp<F>
where
    F: Fn(&mut SqliteConnection) -> Result<T>,
{
    fn run<'a, 'b>(&self, ctx: &'a mut Context<'b, WritableConnection>) -> Result<T> {
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

//
// Write Operation
//

pub struct WriteOp<F> {
    f: F,
}

impl<T, F> Operation<WritableConnection, T> for WriteOp<F>
where
    F: Fn(&mut WritableConnection) -> Result<T>,
{
    fn run<'a, 'b>(&self, ctx: &'a mut Context<'b, WritableConnection>) -> Result<T> {
        (self.f)(ctx.conn())
    }
}

pub fn write_op<T, F>(f: F) -> WriteOp<F>
where
    F: Fn(&mut WritableConnection) -> Result<T>,
{
    WriteOp { f }
}

pub fn run_in_transaction<'a, Op, T>(conn: &'a mut WritableConnection, op: Op) -> Result<T>
where
    Op: Operation<WritableConnection, T>,
{
    conn.immediate_transaction(|conn| op.run(&mut Context::new(conn)))
}
