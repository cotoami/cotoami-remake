use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::prelude::*;

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
    assert_matches!(
        cotonoma,
        Cotonoma {
            node_id,
            ref name,
            coto_id,
            posts: 0,
            ..
        } if node_id == node.uuid &&
             coto_id == coto.uuid &&
             name == "test"
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
    assert_matches!(
        changelog2,
        ChangelogEntry {
            serial_number: 2,
            origin_node_id,
            origin_serial_number: 2,
            change: Change::CreateCotonoma(change_cotonoma, change_coto),
            ..
        } if origin_node_id == node.uuid &&
             change_cotonoma == cotonoma &&
             change_coto == Coto { rowid: 0, ..coto }

    );

    Ok(())
}
