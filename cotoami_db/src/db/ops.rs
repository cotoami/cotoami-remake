//! Basic database operations

use anyhow::Result;
use diesel::{
    dsl::CountStar,
    query_dsl::methods::{LimitDsl, LoadQuery, OffsetDsl, SelectDsl},
    sqlite::SqliteConnection,
    RunQueryDsl,
};
use once_cell::sync::Lazy;
use regex::Regex;

pub(crate) mod changelog_ops;
pub(crate) mod coto_ops;
pub(crate) mod cotonoma_ops;
pub(crate) mod graph_ops;
pub(crate) mod link_ops;
pub(crate) mod node_ops;
pub(crate) mod node_role_ops;

pub(crate) mod prelude {
    pub use super::{node_role_ops::*, *};
}

/////////////////////////////////////////////////////////////////////////////
// Pagination
/////////////////////////////////////////////////////////////////////////////

#[derive(Debug, serde::Serialize, serde::Deserialize)]
pub struct Paginated<T> {
    pub rows: Vec<T>,
    pub page_size: i64,
    pub page_index: i64,
    pub total_rows: i64,
}

impl<T> Paginated<T> {
    pub fn empty_first(page_size: i64) -> Self {
        Self {
            rows: Vec::new(),
            page_size,
            page_index: 0,
            total_rows: 0,
        }
    }

    pub fn total_pages(&self) -> i64 {
        (self.total_rows as f64 / self.page_size as f64).ceil() as i64
    }

    pub fn map<U, F>(self, f: F) -> MappedPage<T, F>
    where
        Self: Sized,
        F: FnMut(T) -> U,
    {
        MappedPage { page: self, f }
    }
}

#[must_use]
pub struct MappedPage<T, F> {
    page: Paginated<T>,
    f: F,
}

impl<T, U, F> From<MappedPage<T, F>> for Paginated<U>
where
    F: FnMut(T) -> U,
{
    fn from(MappedPage { page, f }: MappedPage<T, F>) -> Self {
        Paginated {
            rows: page.rows.into_iter().map(f).collect(),
            page_size: page.page_size,
            page_index: page.page_index,
            total_rows: page.total_rows,
        }
    }
}

/// Returns a paginated results of a query built by `query_builder`.
///
/// Since boxed queries are not cloneable, this function requires a function
/// `query_builder` to create a same query multiple times. It will be possibly
/// called twice, one is to fetch rows and another is to count the total.
/// <https://github.com/diesel-rs/diesel/issues/1698>
fn paginate<'a, R, F, Q>(
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

/// Regular expression to detect CJK characters.
/// FIXME: perhaps it's incomplete.
static CJK: Lazy<Regex> = Lazy::new(|| {
    Regex::new(
        // U+3040 - U+30FF: hiragana and katakana (Japanese only)
        // U+3400 - U+4DBF: CJK unified ideographs extension A (Chinese, Japanese, and Korean)
        // U+4E00 - U+9FFF: CJK unified ideographs (Chinese, Japanese, and Korean)
        // U+F900 - U+FAFF: CJK compatibility ideographs (Chinese, Japanese, and Korean)
        // U+FF66 - U+FF9F: half-width katakana (Japanese only)
        // U+3131 - U+D79D: Korean hangul
        r"[\u{3040}-\u{30ff}\u{3400}-\u{4dbf}\u{4e00}-\u{9fff}\u{f900}-\u{faff}\u{ff66}-\u{ff9f}\u{3131}-\u{d79d}]",
    )
    .unwrap_or_else(|e| unreachable!("{e:?}"))
});

/// Returns true if the given text has CJK characters.
fn detect_cjk_chars(text: &str) -> bool { CJK.is_match(text) }

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

    #[test]
    fn cjk_chars() -> Result<()> {
        assert_eq!(detect_cjk_chars("Hello, world!"), false);
        assert_eq!(detect_cjk_chars("日本語"), true);
        assert_eq!(detect_cjk_chars("光阴似箭"), true);
        assert_eq!(detect_cjk_chars("안녕하세요"), true);
        assert_eq!(detect_cjk_chars("Hello, こんにちは world!"), true);
        Ok(())
    }
}
