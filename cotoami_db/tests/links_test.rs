use anyhow::Result;
use cotoami_db::{prelude::*, time};
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
    let (root_cotonoma, _) = ds.local_node_root()?.unwrap();

    let (coto1, _) = ds.post_coto(&CotoInput::new("coto1"), &root_cotonoma.uuid, &operator)?;
    let (coto2, _) = ds.post_coto(&CotoInput::new("coto2"), &root_cotonoma.uuid, &operator)?;
    let (coto3, _) = ds.post_coto(&CotoInput::new("coto3"), &root_cotonoma.uuid, &operator)?;
    let (coto4, _) = ds.post_coto(&CotoInput::new("coto4"), &root_cotonoma.uuid, &operator)?;

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto2
    /////////////////////////////////////////////////////////////////////////////

    let mock_time = time::mock_time();
    let (link1, changelog) = ds.connect(
        &LinkInput::new(coto1.uuid, coto2.uuid).linking_phrase("hello"),
        &operator,
    )?;

    // check the created link
    assert_that!(
        link1,
        pat!(Link {
            node_id: eq(&node.uuid),
            created_by_id: eq(&node.uuid),
            source_coto_id: eq(&coto1.uuid),
            target_coto_id: eq(&coto2.uuid),
            linking_phrase: some(eq("hello")),
            details: none(),
            order: eq(&1),
            created_at: eq(&mock_time),
            updated_at: eq(&mock_time),
        })
    );

    // check if it is stored in the db
    assert_eq!(ds.link(&link1.uuid)?.as_ref(), Some(&link1));

    // check if `recent_links` contains it
    assert_that!(
        ds.recent_links(None, 5, 0)?,
        pat!(Page {
            size: eq(&5),
            index: eq(&0),
            total_rows: eq(&1),
            rows: elements_are![eq(&link1)]
        })
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&6),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&6),
            change: pat!(Change::CreateLink(eq(&link1)))
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto3
    /////////////////////////////////////////////////////////////////////////////

    time::clear_mock_time();
    let (link2, _) = ds.connect(
        &LinkInput::new(coto1.uuid, coto3.uuid)
            .linking_phrase("bye")
            .details("some details"),
        &operator,
    )?;

    // check the created link
    assert_that!(
        link2,
        pat!(Link {
            source_coto_id: eq(&coto1.uuid),
            target_coto_id: eq(&coto3.uuid),
            linking_phrase: some(eq("bye")),
            details: some(eq("some details")),
            order: eq(&2)
        })
    );

    // check if it is stored in the db
    assert_that!(ds.link(&link2.uuid)?, some(eq(&link2)));

    // check if `recent_links` contains it
    assert_that!(
        ds.recent_links(None, 5, 0)?,
        pat!(Page {
            size: eq(&5),
            index: eq(&0),
            total_rows: eq(&2),
            rows: elements_are![eq(&link2), eq(&link1)]
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto4 with order number 1
    /////////////////////////////////////////////////////////////////////////////

    let (link3, _) = ds.connect(&LinkInput::new(coto1.uuid, coto4.uuid).order(1), &operator)?;

    // check if the order of the links has been updated
    assert_eq!(ds.link(&link3.uuid)?.unwrap().order, 1);
    assert_eq!(ds.link(&link1.uuid)?.unwrap().order, 2);
    assert_eq!(ds.link(&link2.uuid)?.unwrap().order, 3);

    /////////////////////////////////////////////////////////////////////////////
    // When: edit link1
    /////////////////////////////////////////////////////////////////////////////

    let diff = LinkContentDiff::default()
        .linking_phrase(Some("hello"))
        .details(Some("hello details"));
    let (edited_link1, changelog) = ds.edit_link(&link1.uuid, diff, &operator)?;

    // check the edited link
    assert_that!(
        edited_link1,
        pat!(Link {
            linking_phrase: some(eq("hello")),
            details: some(eq("hello details"))
        })
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&9),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&9),
            change: pat!(Change::EditLink {
                link_id: eq(&link1.uuid),
                diff: pat!(LinkContentDiff {
                    linking_phrase: pat!(FieldDiff::Change(eq("hello"))),
                    details: pat!(FieldDiff::Change(eq("hello details"))),
                }),
                updated_at: eq(&edited_link1.updated_at),
            })
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete link1
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds.disconnect(&link1.uuid, &operator)?;

    // check if it is deleted from the db
    assert_eq!(ds.link(&link1.uuid)?, None);

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&10),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&10),
            change: pat!(Change::DeleteLink {
                link_id: eq(&link1.uuid)
            })
        })
    );

    Ok(())
}
