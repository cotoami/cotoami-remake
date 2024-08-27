use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn crud_operations() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup: create coto1, coto2, coto2
    /////////////////////////////////////////////////////////////////////////////

    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let operator = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.root_cotonoma()?.unwrap();

    let (coto1, _) = ds.post_coto(&CotoInput::new("coto1"), &root_cotonoma, &operator)?;
    let (coto2, _) = ds.post_coto(&CotoInput::new("coto2"), &root_cotonoma, &operator)?;
    let (coto3, _) = ds.post_coto(&CotoInput::new("coto3"), &root_cotonoma, &operator)?;
    let (coto4, _) = ds.post_coto(&CotoInput::new("coto4"), &root_cotonoma, &operator)?;

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto2
    /////////////////////////////////////////////////////////////////////////////

    let (link1, changelog1) = ds.connect(
        (&coto1.uuid, &coto2.uuid),
        Some("hello"),
        None,
        None,
        Some(&root_cotonoma),
        &operator,
    )?;

    // check the created link
    assert_that!(
        link1,
        matches_pattern!(Link {
            node_id: eq(node.uuid),
            created_in_id: some(eq(root_cotonoma.uuid)),
            created_by_id: eq(node.uuid),
            source_coto_id: eq(coto1.uuid),
            target_coto_id: eq(coto2.uuid),
            linking_phrase: some(eq("hello")),
            details: none(),
            order: eq(1)
        })
    );
    common::assert_approximately_now(link1.created_at());
    common::assert_approximately_now(link1.updated_at());

    // check if it is stored in the db
    assert_eq!(ds.link(&link1.uuid)?.as_ref(), Some(&link1));

    // check if `recent_links` contains it
    assert_that!(
        ds.recent_links(None, Some(&root_cotonoma.uuid), 5, 0)?,
        matches_pattern!(Paginated {
            page_size: eq(5),
            page_index: eq(0),
            total_rows: eq(1),
            rows: elements_are![eq_deref_of(&link1)]
        })
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog1,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(6),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(6),
            change: matches_pattern!(Change::CreateLink(eq_deref_of(&link1)))
        })
    );

    // check if the number of outgoing links has been incremented
    assert_eq!(ds.coto(&coto1.uuid)?.unwrap().outgoing_links, 1);

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto3
    /////////////////////////////////////////////////////////////////////////////

    let (link2, _) = ds.connect(
        (&coto1.uuid, &coto3.uuid),
        Some("bye"),
        Some("some details"),
        None,
        Some(&root_cotonoma),
        &operator,
    )?;

    // check the created link
    assert_that!(
        link2,
        matches_pattern!(Link {
            source_coto_id: eq(coto1.uuid),
            target_coto_id: eq(coto3.uuid),
            linking_phrase: some(eq("bye")),
            details: some(eq("some details")),
            order: eq(2)
        })
    );

    // check if it is stored in the db
    assert_that!(ds.link(&link2.uuid)?, some(eq_deref_of(&link2)));

    // check if `recent_links` contains it
    assert_that!(
        ds.recent_links(None, Some(&root_cotonoma.uuid), 5, 0)?,
        matches_pattern!(Paginated {
            page_size: eq(5),
            page_index: eq(0),
            total_rows: eq(2),
            rows: elements_are![eq_deref_of(&link2), eq_deref_of(&link1)]
        })
    );

    // check if the number of outgoing links has been incremented
    assert_eq!(ds.coto(&coto1.uuid)?.unwrap().outgoing_links, 2);

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto4 with order number 1
    /////////////////////////////////////////////////////////////////////////////

    let (link3, _) = ds.connect(
        (&coto1.uuid, &coto4.uuid),
        None,
        None,
        Some(1),
        Some(&root_cotonoma),
        &operator,
    )?;

    // check if the order of the links has been updated
    assert_eq!(ds.link(&link3.uuid)?.unwrap().order, 1);
    assert_eq!(ds.link(&link1.uuid)?.unwrap().order, 2);
    assert_eq!(ds.link(&link2.uuid)?.unwrap().order, 3);

    // check if the number of outgoing links has been incremented
    assert_eq!(ds.coto(&coto1.uuid)?.unwrap().outgoing_links, 3);

    /////////////////////////////////////////////////////////////////////////////
    // When: edit link1
    /////////////////////////////////////////////////////////////////////////////

    let (edited_link1, changelog2) =
        ds.edit_link(&link1.uuid, Some("hello"), Some("hello details"), &operator)?;

    // check the edited link
    assert_that!(
        edited_link1,
        matches_pattern!(Link {
            linking_phrase: some(eq("hello")),
            details: some(eq("hello details"))
        })
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog2,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(9),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(9),
            change: matches_pattern!(Change::EditLink {
                link_id: eq(link1.uuid),
                linking_phrase: some(eq("hello")),
                details: some(eq("hello details")),
                updated_at: eq(edited_link1.updated_at),
            })
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete link1
    /////////////////////////////////////////////////////////////////////////////

    let changelog3 = ds.delete_link(&link1.uuid, &operator)?;

    // check if it is deleted from the db
    assert_eq!(ds.link(&link1.uuid)?, None);

    // check the content of the ChangelogEntry
    assert_that!(
        changelog3,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(10),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(10),
            change: matches_pattern!(Change::DeleteLink(eq(link1.uuid)))
        })
    );

    // check if the number of outgoing links has been decremented
    assert_eq!(ds.coto(&coto1.uuid)?.unwrap().outgoing_links, 2);

    Ok(())
}
