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

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto2
    /////////////////////////////////////////////////////////////////////////////
    let (link1, changelog1) = ds.create_link(
        &coto1.uuid,
        &coto2.uuid,
        Some("hello"),
        None,
        Some(&root_cotonoma),
        &operator,
    )?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

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
    assert_eq!(ds.link(&link1.uuid)?, Some(link1.clone()));

    // check if `recent_links` contains it
    let recent_links = ds.recent_links(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(recent_links.rows.len(), 1);
    assert_eq!(recent_links.rows[0], link1);

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog1,
        ChangelogEntry {
            serial_number: 5,
            origin_node_id,
            origin_serial_number: 5,
            change: Change::CreateLink(change_link),
            ..
        } if origin_node_id == node.uuid &&
             change_link == link1
    );

    // check if the number of outgoing links has been incremented
    let Some(coto) = ds.coto(&coto1.uuid)? else { panic!() };
    assert_eq!(coto.outgoing_links, 1);

    /////////////////////////////////////////////////////////////////////////////
    // When: create a link from coto1 to coto3
    /////////////////////////////////////////////////////////////////////////////
    let (link2, _) = ds.create_link(
        &coto1.uuid,
        &coto3.uuid,
        Some("bye"),
        Some("some details"),
        Some(&root_cotonoma),
        &operator,
    )?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    // check the created link
    assert_matches!(
        link2,
        Link {
            source_coto_id,
            target_coto_id,
            linking_phrase: Some(ref linking_phrase),
            details: Some(ref details),
            ..
        } if source_coto_id == coto1.uuid &&
             target_coto_id == coto3.uuid &&
             linking_phrase == "bye" &&
             details == "some details"
    );

    // check if it is stored in the db
    assert_eq!(ds.link(&link2.uuid)?, Some(link2.clone()));

    // check if `recent_links` contains it
    let recent_links = ds.recent_links(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(recent_links.rows.len(), 2);
    assert_eq!(recent_links.rows[0], link2);
    assert_eq!(recent_links.rows[1], link1);

    // check if the number of outgoing links has been incremented
    let Some(coto) = ds.coto(&coto1.uuid)? else { panic!() };
    assert_eq!(coto.outgoing_links, 2);

    /////////////////////////////////////////////////////////////////////////////
    // When: edit link1
    /////////////////////////////////////////////////////////////////////////////
    let (edited_link1, changelog2) =
        ds.edit_link(&link1.uuid, Some("hello"), Some("hello details"), &operator)?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

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
            serial_number: 7,
            origin_node_id,
            origin_serial_number: 7,
            change: Change::EditLink {
                uuid,
                linking_phrase: Some(ref linking_phrase),
                details: Some(ref details),
                updated_at,
            },
            ..
        } if origin_node_id == node.uuid &&
             uuid == link1.uuid &&
             linking_phrase == "hello" &&
             details == "hello details" &&
             updated_at == edited_link1.updated_at
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete link1
    /////////////////////////////////////////////////////////////////////////////
    let changelog3 = ds.delete_link(&link1.uuid, &operator)?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    // check if it is deleted from the db
    assert_eq!(ds.link(&link1.uuid)?, None);

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog3,
        ChangelogEntry {
            serial_number: 8,
            origin_node_id,
            origin_serial_number: 8,
            change: Change::DeleteLink (change_link_id),
            ..
        } if origin_node_id == node.uuid &&
             change_link_id == link1.uuid
    );

    // check if the number of outgoing links has been decremented
    let Some(coto) = ds.coto(&coto1.uuid)? else { panic!() };
    assert_eq!(coto.outgoing_links, 1);

    Ok(())
}
