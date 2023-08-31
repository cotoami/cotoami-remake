use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::prelude::*;

pub mod common;

#[test]
fn crud_operations() -> Result<()> {
    // setup
    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut session = db.create_session()?;
    let operator = session.local_node_as_operator()?;
    let (root_cotonoma, _) = session.get_root_cotonoma()?.unwrap();

    // when: post_coto
    let (coto, changelog2) = session.post_coto("hello", None, &root_cotonoma, &operator)?;

    // then
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

    assert_eq!(session.get_coto(&coto.uuid)?, Some(coto.clone()));

    let recent_cotos = session.recent_cotos(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(recent_cotos.rows.len(), 1);
    assert_eq!(recent_cotos.rows[0], coto);

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

    // when: edit_coto
    let (edited_coto, changelog3) = session.edit_coto(&coto.uuid, "bar", Some("foo"), &operator)?;

    // then
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

    assert_eq!(session.get_coto(&coto.uuid)?, Some(edited_coto.clone()));

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

    // when: delete_coto
    let changelog4 = session.delete_coto(&coto.uuid, &operator)?;

    // then
    assert_eq!(session.get_coto(&coto.uuid)?, None);
    let all_cotos = session.recent_cotos(None, Some(&root_cotonoma.uuid), 5, 0)?;
    assert_eq!(all_cotos.rows.len(), 0);

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

    Ok(())
}
