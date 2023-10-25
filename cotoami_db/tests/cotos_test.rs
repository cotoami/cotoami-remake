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
    let mut session = db.new_session()?;
    let operator = session.local_node_as_operator()?;
    let (root_cotonoma, _) = session.root_cotonoma()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // When: post_coto
    /////////////////////////////////////////////////////////////////////////////
    let (coto, changelog2) = session.post_coto("hello", None, &root_cotonoma, &operator)?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    // check the inserted coto
    assert_matches!(
        coto,
        Coto {
            node_id,
            posted_in_id,
            posted_by_id,
            content: Some(ref content),
            summary: None,
            is_cotonoma: false,
            repost_of_id: None,
            reposted_in_ids: None,
            ..
        } if node_id == node.uuid &&
             posted_in_id == Some(root_cotonoma.uuid) &&
             posted_by_id == node.uuid &&
             content == "hello"
    );
    common::assert_approximately_now(coto.created_at());
    common::assert_approximately_now(coto.updated_at());

    // check if it is stored in the db
    assert_eq!(session.coto(&coto.uuid)?, Some(coto.clone()));

    // check if `recent_cotos` contains it
    let recent_cotos = session.recent_cotos(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(recent_cotos.rows.len(), 1);
    assert_eq!(recent_cotos.rows[0], coto);

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog2,
        ChangelogEntry {
            serial_number: 2,
            origin_node_id,
            origin_serial_number: 2,
            change: Change::CreateCoto(change_coto),
            ..
        } if origin_node_id == node.uuid &&
             change_coto == Coto { rowid: 0, ..coto }
    );

    // check if the `number_of_posts` in the cotonoma has been incremented
    let (cotonoma, _) = session.cotonoma_or_err(&root_cotonoma.uuid)?;
    assert_eq!(cotonoma.number_of_posts, 1);

    /////////////////////////////////////////////////////////////////////////////
    // When: edit_coto
    /////////////////////////////////////////////////////////////////////////////
    let (edited_coto, changelog3) = session.edit_coto(&coto.uuid, "bar", Some("foo"), &operator)?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    // check the edited coto
    assert_matches!(
        edited_coto,
        Coto {
            node_id,
            posted_in_id,
            posted_by_id,
            content: Some(ref content),
            summary: Some(ref summary),
            is_cotonoma: false,
            repost_of_id: None,
            reposted_in_ids: None,
            ..
        } if node_id == node.uuid &&
             posted_in_id == Some(root_cotonoma.uuid) &&
             posted_by_id == node.uuid &&
             content == "bar" &&
             summary == "foo"
    );
    common::assert_approximately_now(edited_coto.updated_at());

    // check if it is stored in the db
    assert_eq!(session.coto(&coto.uuid)?, Some(edited_coto.clone()));

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog3,
        ChangelogEntry {
            serial_number: 3,
            origin_node_id,
            origin_serial_number: 3,
            change: Change::EditCoto {
                uuid,
                content,
                summary: Some(ref summary),
                updated_at,
            },
            ..
        } if origin_node_id == node.uuid &&
             uuid == coto.uuid &&
             content == "bar" &&
             summary == "foo" &&
             updated_at == edited_coto.updated_at
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete_coto
    /////////////////////////////////////////////////////////////////////////////
    let changelog4 = session.delete_coto(&coto.uuid, &operator)?;

    /////////////////////////////////////////////////////////////////////////////
    // Then
    /////////////////////////////////////////////////////////////////////////////

    // check if it is deleted from the db
    assert_eq!(session.coto(&coto.uuid)?, None);
    let all_cotos = session.recent_cotos(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(all_cotos.rows.len(), 0);

    // check the content of the ChangelogEntry
    assert_matches!(
        changelog4,
        ChangelogEntry {
            serial_number: 4,
            origin_node_id,
            origin_serial_number: 4,
            change: Change::DeleteCoto (change_coto_id),
            ..
        } if origin_node_id == node.uuid &&
             change_coto_id == coto.uuid
    );

    // check if the `number_of_posts` in the cotonoma has been decremented
    let (cotonoma, _) = session.cotonoma_or_err(&root_cotonoma.uuid)?;
    assert_eq!(cotonoma.number_of_posts, 0);

    Ok(())
}
