use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn pagination() -> Result<()> {
    // setup
    let (_root_dir, db, _node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let operator = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.root_cotonoma()?.unwrap();

    // when
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_that!(
        paginated,
        matches_pattern!(Paginated {
            page_size: eq(2),
            page_index: eq(0),
            total_rows: eq(0),
            rows: empty(),
        })
    );
    assert_eq!(paginated.total_pages(), 0);

    // when
    let _ = ds.post_coto(&CotoContent::new("1"), &root_cotonoma, &operator)?;
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_that!(
        paginated,
        matches_pattern!(Paginated {
            page_size: eq(2),
            page_index: eq(0),
            total_rows: eq(1),
            rows: elements_are![matches_pattern!(Coto {
                content: some(eq("1"))
            })],
        })
    );
    assert_eq!(paginated.total_pages(), 1);

    // when
    let _ = ds.post_coto(&CotoContent::new("2"), &root_cotonoma, &operator)?;
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_that!(
        paginated,
        matches_pattern!(Paginated {
            page_size: eq(2),
            page_index: eq(0),
            total_rows: eq(2),
            rows: elements_are![
                matches_pattern!(Coto {
                    content: some(eq("2"))
                }),
                matches_pattern!(Coto {
                    content: some(eq("1"))
                })
            ],
        })
    );
    assert_eq!(paginated.total_pages(), 1);

    // when
    let _ = ds.post_coto(&CotoContent::new("3"), &root_cotonoma, &operator)?;
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 0)?;

    // then
    assert_that!(
        paginated,
        matches_pattern!(Paginated {
            page_size: eq(2),
            page_index: eq(0),
            total_rows: eq(3),
            rows: elements_are![
                matches_pattern!(Coto {
                    content: some(eq("3"))
                }),
                matches_pattern!(Coto {
                    content: some(eq("2"))
                })
            ],
        })
    );
    assert_eq!(paginated.total_pages(), 2);

    // when
    let paginated = ds.recent_cotos(None, Some(&root_cotonoma.uuid), 2, 1)?;

    // then
    assert_that!(
        paginated,
        matches_pattern!(Paginated {
            page_size: eq(2),
            page_index: eq(1),
            total_rows: eq(3),
            rows: elements_are![matches_pattern!(Coto {
                content: some(eq("1"))
            })],
        })
    );
    assert_eq!(paginated.total_pages(), 2);

    Ok(())
}
