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
    // When: post_coto
    /////////////////////////////////////////////////////////////////////////////

    let (coto, changelog2) = ds.post_coto(CotoContent::new("hello"), &root_cotonoma, &operator)?;

    // check the inserted coto
    assert_that!(
        coto,
        matches_pattern!(Coto {
            node_id: eq(node.uuid),
            posted_in_id: some(eq(root_cotonoma.uuid)),
            posted_by_id: eq(node.uuid),
            content: some(eq("hello")),
            summary: none(),
            is_cotonoma: eq(false),
            repost_of_id: none(),
            reposted_in_ids: none()
        })
    );
    common::assert_approximately_now(coto.created_at());
    common::assert_approximately_now(coto.updated_at());
    assert_eq!(coto.created_at, coto.updated_at);

    // check if it is stored in the db
    assert!(ds.contains_coto(&coto.uuid)?);
    assert_that!(ds.coto(&coto.uuid)?, some(eq_deref_of(&coto)));

    // check if `recent_cotos` contains it
    assert_that!(
        ds.recent_cotos(None, Some(&root_cotonoma.uuid), 5, 0)?,
        matches_pattern!(Paginated {
            page_size: eq(5),
            page_index: eq(0),
            total_rows: eq(1),
            rows: elements_are![eq_deref_of(&coto)]
        })
    );

    // check if the number of posts of the root cotonoma has been incremented
    let (root_cotonoma, _) = ds.try_get_cotonoma(&root_cotonoma.uuid)?;
    assert_that!(
        root_cotonoma,
        matches_pattern!(Cotonoma {
            posts: eq(1),
            updated_at: eq(coto.updated_at)
        })
    );

    // check the super cotonomas
    assert_that!(
        ds.super_cotonomas(&coto)?,
        elements_are![eq_deref_of(&root_cotonoma)]
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog2,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(2),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(2),
            change: matches_pattern!(Change::CreateCoto(eq(Coto { rowid: 0, ..coto })))
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: edit_coto
    /////////////////////////////////////////////////////////////////////////////

    let (edited_coto, changelog3) = ds.edit_coto(&coto.uuid, "bar", Some("foo"), &operator)?;

    // check the edited coto
    assert_that!(
        edited_coto,
        matches_pattern!(Coto {
            node_id: eq(node.uuid),
            posted_in_id: some(eq(root_cotonoma.uuid)),
            posted_by_id: eq(node.uuid),
            content: some(eq("bar")),
            summary: some(eq("foo")),
            is_cotonoma: eq(false),
            repost_of_id: none(),
            reposted_in_ids: none(),
        })
    );
    common::assert_approximately_now(edited_coto.updated_at());

    // check if it is stored in the db
    assert_that!(ds.coto(&coto.uuid)?, some(eq_deref_of(&edited_coto)));

    // check the content of the ChangelogEntry
    assert_that!(
        changelog3,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(3),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(3),
            change: matches_pattern!(Change::EditCoto {
                coto_id: eq(coto.uuid),
                content: eq("bar"),
                summary: some(eq("foo")),
                updated_at: eq(edited_coto.updated_at),
            })
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete_coto
    /////////////////////////////////////////////////////////////////////////////

    let changelog4 = ds.delete_coto(&coto.uuid, &operator)?;

    // check if it is deleted from the db
    assert!(!ds.contains_coto(&coto.uuid)?);
    assert_eq!(ds.coto(&coto.uuid)?, None);
    assert_that!(
        ds.recent_cotos(None, Some(&root_cotonoma.uuid), 5, 0)?,
        matches_pattern!(Paginated {
            page_size: eq(5),
            page_index: eq(0),
            total_rows: eq(0)
        })
    );

    // check if the number of posts in the cotonoma has been decremented
    let (cotonoma, _) = ds.try_get_cotonoma(&root_cotonoma.uuid)?;
    assert_eq!(cotonoma.posts, 0);

    // check the content of the ChangelogEntry
    assert_that!(
        changelog4,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(4),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(4),
            change: matches_pattern!(Change::DeleteCoto {
                coto_id: eq(coto.uuid),
                deleted_at: eq(cotonoma.updated_at)
            })
        })
    );

    Ok(())
}
