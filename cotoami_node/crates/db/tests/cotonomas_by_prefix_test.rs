use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn cotonomas_by_prefix() -> Result<()> {
    // Setup
    let (_root_dir, db, _node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root, _) = ds.local_node_root()?.unwrap();

    let _ = ds.post_cotonoma(&CotonomaInput::new("abc"), &root, &opr)?;
    let _ = ds.post_cotonoma(&CotonomaInput::new("abcdef"), &root, &opr)?;
    let _ = ds.post_cotonoma(&CotonomaInput::new("foo"), &root, &opr)?;
    let _ = ds.post_cotonoma(&CotonomaInput::new("abcabc"), &root, &opr)?;

    // When: limit 1 (only exact matches)
    let cotonomas = ds.cotonomas_by_prefix("abc", None, 1)?;
    assert_that!(
        cotonomas,
        elements_are![pat!(Cotonoma {
            name: eq("abc"),
            ..
        }),]
    );

    // When: limit 2 (both exact and prefix matches)
    let cotonomas = ds.cotonomas_by_prefix("abc", None, 2)?;
    assert_that!(
        cotonomas,
        elements_are![
            // exact matches should come first
            pat!(Cotonoma {
                name: eq("abc"),
                ..
            }),
            pat!(Cotonoma {
                name: eq("abcabc"),
                ..
            }),
        ]
    );

    // When: limit 5 (all)
    let cotonomas = ds.cotonomas_by_prefix("abc", None, 5)?;
    assert_that!(
        cotonomas,
        elements_are![
            // exact matches should come first
            pat!(Cotonoma {
                name: eq("abc"),
                ..
            }),
            pat!(Cotonoma {
                name: eq("abcabc"),
                ..
            }),
            pat!(Cotonoma {
                name: eq("abcdef"),
                ..
            })
        ]
    );

    // When: '%' should be escaped
    let cotonomas = ds.cotonomas_by_prefix("%cde", None, 5)?;
    assert_that!(cotonomas, is_empty());

    Ok(())
}
