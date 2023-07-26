use std::marker::PhantomData;

use anyhow::Result;

use super::{Context, Operation};

/// Maps an `Operation<Conn, T>` to `Operation<Conn, U>` by applying a function
pub fn map<Op, Conn, T, U, F>(op: Op, f: F) -> MappedOp<Op, T, F>
where
    Op: Operation<Conn, T>,
    F: FnOnce(T) -> U,
{
    MappedOp {
        op,
        _t: PhantomData,
        f,
    }
}

#[derive(Debug)]
#[must_use]
pub struct MappedOp<Op, T, F> {
    op: Op,
    _t: PhantomData<fn() -> T>,
    f: F,
}

impl<Op, Conn, T, U, F> Operation<Conn, U> for MappedOp<Op, T, F>
where
    Op: Operation<Conn, T>,
    F: FnOnce(T) -> U,
{
    fn run(self, ctx: &mut Context<'_, Conn>) -> Result<U> {
        let MappedOp { op, f, .. } = self;
        op.run(ctx).map(f)
    }
}
