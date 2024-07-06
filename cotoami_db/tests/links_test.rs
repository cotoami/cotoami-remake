use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::prelude::*;

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

    let (coto1, _) = ds.post_coto("coto1", None, &root_cotonoma, &operator)?;
    let (coto2, _) = ds.post_coto("coto2", None, &root_cotonoma, &operator)?;
    let (coto3, _) = ds.post_coto("coto3", None, &root_cotonoma, &operator)?;
    let (coto4, _) = ds.post_coto("coto4", None, &root_cotonoma, &operator)?;

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
    assert_matches!(
        link1,
        Link {
            node_id,
            created_in_id,
            created_by_id,
            source_coto_id,
            target_coto_id,
            linking_phrase: Some(ref linking_phrase),
            details: None,
            order: 1,
            ..
        } if node_id == node.uuid &&
             created_in_id == Some(root_cotonoma.uuid) &&
             created_by_id == node.uuid &&
             source_coto_id == coto1.uuid &&
             target_coto_id == coto2.uuid &&
             linking_phrase == "hello"
    );
    common::assert_approximately_now(link1.created_at());
    common::assert_approximately_now(link1.updated_at());

    // check if it is stored in the db
    assert_eq!(ds.link(&link1.uuid)?.as_ref(), Some(&link1));

    // check if `recent_links` contains it
    let recent_links = ds.recent_links(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(recent_links.rows.len(), 1);
    assert_eq!(recent_links.rows[0], link1);

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog1,
        ChangelogEntry {
            serial_number: 6,
            origin_node_id,
            origin_serial_number: 6,
            change: Change::CreateLink(link),
            ..
        } if origin_node_id == node.uuid &&
             link == link1
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
    assert_matches!(
        link2,
        Link {
            source_coto_id,
            target_coto_id,
            linking_phrase: Some(ref linking_phrase),
            details: Some(ref details),
            order: 2,
            ..
        } if source_coto_id == coto1.uuid &&
             target_coto_id == coto3.uuid &&
             linking_phrase == "bye" &&
             details == "some details"
    );

    // check if it is stored in the db
    assert_eq!(ds.link(&link2.uuid)?.as_ref(), Some(&link2));

    // check if `recent_links` contains it
    let recent_links = ds.recent_links(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(recent_links.rows.len(), 2);
    assert_eq!(recent_links.rows[0], link2);
    assert_eq!(recent_links.rows[1], link1);

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
    assert_matches!(
        edited_link1,
        Link {
            linking_phrase: Some(ref linking_phrase),
            details: Some(ref details),
            ..
        } if linking_phrase == "hello" &&
             details == "hello details"
    );

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog2,
        ChangelogEntry {
            serial_number: 9,
            origin_node_id,
            origin_serial_number: 9,
            change: Change::EditLink {
                link_id,
                linking_phrase: Some(ref linking_phrase),
                details: Some(ref details),
                updated_at,
            },
            ..
        } if origin_node_id == node.uuid &&
             link_id == link1.uuid &&
             linking_phrase == "hello" &&
             details == "hello details" &&
             updated_at == edited_link1.updated_at
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete link1
    /////////////////////////////////////////////////////////////////////////////

    let changelog3 = ds.delete_link(&link1.uuid, &operator)?;

    // check if it is deleted from the db
    assert_eq!(ds.link(&link1.uuid)?, None);

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog3,
        ChangelogEntry {
            serial_number: 10,
            origin_node_id,
            origin_serial_number: 10,
            change: Change::DeleteLink (change_link_id),
            ..
        } if origin_node_id == node.uuid &&
             change_link_id == link1.uuid
    );

    // check if the number of outgoing links has been decremented
    assert_eq!(ds.coto(&coto1.uuid)?.unwrap().outgoing_links, 2);

    Ok(())
}
