use super::{Context, Operation};
use anyhow::Result;
use std::marker::PhantomData;

/// Creates a chain with another operation that depends on the result of this operation
pub fn and_then<Op1, Op2, Conn, T, U, F>(op: Op1, f: F) -> AndThenOp<Op1, T, F>
where
    Op1: Operation<Conn, T>,
    Op2: Operation<Conn, U>,
    F: Fn(T) -> Op2,
{
    AndThenOp {
        op,
        _t: PhantomData,
        f,
    }
}

#[derive(Debug)]
#[must_use]
pub struct AndThenOp<Op1, T, F> {
    op: Op1,
    _t: PhantomData<fn() -> T>,
    f: F,
}

impl<Op1, Op2, Conn, T, U, F> Operation<Conn, U> for AndThenOp<Op1, T, F>
where
    Op1: Operation<Conn, T>,
    Op2: Operation<Conn, U>,
    F: Fn(T) -> Op2,
{
    fn run(&self, ctx: &mut Context<'_, Conn>) -> Result<U> {
        let AndThenOp { ref op, ref f, .. } = self;
        op.run(ctx).and_then(|t| f(t).run(ctx))
    }
}

/// A function that takes a [Context] as a parameter can be used as an [Operation]
impl<Conn, T, F> Operation<Conn, T> for F
where
    F: Fn(&mut Context<'_, Conn>) -> Result<T>,
{
    fn run(&self, ctx: &mut Context<'_, Conn>) -> Result<T> {
        self(ctx)
    }
}
