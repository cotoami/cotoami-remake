use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn pagination() -> Result<()> {
    // setup
    let (_root_dir, db, _node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.local_node_root()?.unwrap();

    // when
    let paginated = ds.recent_cotos(
        Scope::Cotonoma((root_cotonoma.uuid, CotonomaScope::Local)),
        false,
        2,
        0,
    )?;

    // then
    assert_that!(
        paginated,
        pat!(Page {
            size: eq(&2),
            index: eq(&0),
            total_rows: eq(&0),
            rows: is_empty(),
        })
    );
    assert_eq!(paginated.total_pages(), 0);

    // when
    let _ = ds.post_coto(&CotoInput::new("1"), &root_cotonoma.uuid, &opr)?;
    let paginated = ds.recent_cotos(
        Scope::Cotonoma((root_cotonoma.uuid, CotonomaScope::Local)),
        false,
        2,
        0,
    )?;

    // then
    assert_that!(
        paginated,
        pat!(Page {
            size: eq(&2),
            index: eq(&0),
            total_rows: eq(&1),
            rows: elements_are![pat!(Coto {
                content: some(eq("1")),
                ..
            })],
        })
    );
    assert_eq!(paginated.total_pages(), 1);

    // when
    let _ = ds.post_coto(&CotoInput::new("2"), &root_cotonoma.uuid, &opr)?;
    let paginated = ds.recent_cotos(
        Scope::Cotonoma((root_cotonoma.uuid, CotonomaScope::Local)),
        false,
        2,
        0,
    )?;

    // then
    assert_that!(
        paginated,
        pat!(Page {
            size: eq(&2),
            index: eq(&0),
            total_rows: eq(&2),
            rows: elements_are![
                pat!(Coto {
                    content: some(eq("2")),
                    ..
                }),
                pat!(Coto {
                    content: some(eq("1")),
                    ..
                })
            ],
        })
    );
    assert_eq!(paginated.total_pages(), 1);

    // when
    let _ = ds.post_coto(&CotoInput::new("3"), &root_cotonoma.uuid, &opr)?;
    let paginated = ds.recent_cotos(
        Scope::Cotonoma((root_cotonoma.uuid, CotonomaScope::Local)),
        false,
        2,
        0,
    )?;

    // then
    assert_that!(
        paginated,
        pat!(Page {
            size: eq(&2),
            index: eq(&0),
            total_rows: eq(&3),
            rows: elements_are![
                pat!(Coto {
                    content: some(eq("3")),
                    ..
                }),
                pat!(Coto {
                    content: some(eq("2")),
                    ..
                })
            ],
        })
    );
    assert_eq!(paginated.total_pages(), 2);

    // when
    let paginated = ds.recent_cotos(
        Scope::Cotonoma((root_cotonoma.uuid, CotonomaScope::Local)),
        false,
        2,
        1,
    )?;

    // then
    assert_that!(
        paginated,
        pat!(Page {
            size: eq(&2),
            index: eq(&1),
            total_rows: eq(&3),
            rows: elements_are![pat!(Coto {
                content: some(eq("1")),
                ..
            })],
        })
    );
    assert_eq!(paginated.total_pages(), 2);

    Ok(())
}
