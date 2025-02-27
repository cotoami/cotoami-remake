use anyhow::Result;
use diesel::sqlite::SqliteConnection;
use once_cell::unsync::OnceCell;
use parking_lot::MutexGuard;

use crate::db::{
    op,
    op::{Operation, WritableConn},
    Globals,
};

pub mod changes;
pub mod cotonomas;
pub mod cotos;
pub mod graph;
pub mod itos;
pub mod nodes;

pub struct DatabaseSession<'a> {
    globals: &'a Globals,
    ro_conn: OnceCell<SqliteConnection>,

    // The following fields were once defined as generic types. However,
    // it turned out to make it awkward to use the `DatabaseSession` type
    // because of the trait bounds, which unnecessarily expose
    // the internal concerns (`SqliteConnection` and `WritableConn`) to users of the type.
    new_ro_conn: Box<dyn Fn() -> Result<SqliteConnection> + 'a>,
    lock_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConn> + 'a>,
}

impl<'a> DatabaseSession<'a> {
    pub(super) fn new(
        globals: &'a Globals,
        new_ro_conn: Box<dyn Fn() -> Result<SqliteConnection> + 'a>,
        lock_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConn> + 'a>,
    ) -> Self {
        Self {
            globals,
            ro_conn: OnceCell::new(),
            new_ro_conn,
            lock_rw_conn,
        }
    }

    fn ro_conn(&mut self) -> Result<&mut SqliteConnection> {
        // https://github.com/matklad/once_cell/issues/194
        let _ = self.ro_conn.get_or_try_init(|| (self.new_ro_conn)())?;
        Ok(self.ro_conn.get_mut().unwrap_or_else(|| unreachable!()))
    }

    /// Runs a read operation in snapshot isolation.
    fn read_transaction<Op, T>(&mut self, op: Op) -> Result<T>
    where
        Op: Operation<SqliteConnection, T>,
    {
        op::run_read(self.ro_conn()?, op)
    }

    /// Runs a read/write operation.
    fn write_transaction<Op, T>(&self, op: Op) -> Result<T>
    where
        Op: Operation<WritableConn, T>,
    {
        op::run_write(&mut (self.lock_rw_conn)(), op)
    }
}
