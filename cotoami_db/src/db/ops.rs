//! Basic database operations

use anyhow::Result;
use diesel::{
    dsl::CountStar,
    query_dsl::methods::{LimitDsl, LoadQuery, OffsetDsl, SelectDsl},
    sqlite::SqliteConnection,
    RunQueryDsl,
};

use crate::models::node::child::ChildNode;

pub mod changelog_ops;
pub mod child_node_ops;
pub mod coto_ops;
pub mod cotonoma_ops;
pub mod link_ops;
pub mod local_node_ops;
pub mod node_ops;

/////////////////////////////////////////////////////////////////////////////
// Operator
/////////////////////////////////////////////////////////////////////////////

pub enum Operator {
    Owner,
    ChildNode(ChildNode),
}

/////////////////////////////////////////////////////////////////////////////
// Pagination
/////////////////////////////////////////////////////////////////////////////

#[derive(serde::Serialize)]
pub struct Paginated<T> {
    pub rows: Vec<T>,
    pub page_size: i64,
    pub page_index: i64,
    pub total_rows: i64,
}

impl<T> Paginated<T> {
    pub fn total_pages(&self) -> i64 {
        (self.total_rows as f64 / self.page_size as f64).ceil() as i64
    }
}

/// Returns a paginated results of a query built by `query_builder`.
///
/// Since boxed queries are not cloneable, this function requires a function
/// `query_builder` to create a same query multiple times. It will be possibly
/// called twice, one is to fetch rows and another is to count the total.
/// <https://github.com/diesel-rs/diesel/issues/1698>
pub fn paginate<'a, R, F, Q>(
    conn: &mut SqliteConnection,
    page_size: i64,
    page_index: i64,
    query_builder: F,
) -> Result<Paginated<R>>
where
    F: Fn() -> Q,
    Q: LimitDsl + SelectDsl<CountStar>,
    <Q as LimitDsl>::Output: OffsetDsl,
    <<Q as LimitDsl>::Output as OffsetDsl>::Output: LoadQuery<'a, SqliteConnection, R>,
    <Q as SelectDsl<CountStar>>::Output: LoadQuery<'a, SqliteConnection, i64>,
{
    let rows = query_builder()
        .limit(page_size)
        .offset(page_index * page_size)
        .load::<R>(conn)?;

    let count_rows = rows.len() as i64;
    let total_rows = if page_index == 0 && count_rows < page_size {
        count_rows
    } else {
        query_builder()
            .select(diesel::dsl::count_star())
            .get_result(conn)?
    };

    Ok(Paginated {
        rows,
        page_size,
        page_index,
        total_rows,
    })
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn total_pages() -> Result<()> {
        let mut paginated: Paginated<usize> = Paginated {
            rows: Vec::new(),
            page_size: 2,
            page_index: 0,
            total_rows: 0,
        };
        assert_eq!(paginated.total_pages(), 0);

        paginated.total_rows = 1;
        assert_eq!(paginated.total_pages(), 1);

        paginated.total_rows = 2;
        assert_eq!(paginated.total_pages(), 1);

        paginated.total_rows = 3;
        assert_eq!(paginated.total_pages(), 2);

        Ok(())
    }
}
