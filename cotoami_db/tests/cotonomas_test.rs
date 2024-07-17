use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn crud_operations() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let operator = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.root_cotonoma()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // When: post_cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let ((cotonoma, coto), changelog2) = ds.post_cotonoma("test", &root_cotonoma, &operator)?;

    // check the inserted cotonoma/coto
    assert_that!(
        cotonoma,
        matches_pattern!(Cotonoma {
            node_id: eq(node.uuid),
            name: eq("test"),
            coto_id: eq(coto.uuid),
            posts: eq(0)
        })
    );
    common::assert_approximately_now(cotonoma.created_at());
    common::assert_approximately_now(cotonoma.updated_at());
    assert_eq!(cotonoma.created_at, cotonoma.updated_at);
    assert_eq!(coto.created_at, coto.updated_at);
    assert_eq!(cotonoma.created_at, coto.created_at);

    // check if the number of posts of the root cotonoma has been incremented
    let (root_cotonoma, _) = ds.try_get_cotonoma(&root_cotonoma.uuid)?;
    assert_eq!(root_cotonoma.posts, 1);
    assert_eq!(root_cotonoma.updated_at, coto.created_at);

    // check the content of the ChangelogEntry
    assert_that!(
        changelog2,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(2),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(2),
            change: matches_pattern!(Change::CreateCotonoma(
                eq(cotonoma),
                eq(Coto { rowid: 0, ..coto })
            )),
        })
    );

    Ok(())
}
