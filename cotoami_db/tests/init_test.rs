use anyhow::Result;
use assert_matches::assert_matches;
use chrono::{offset::Utc, Duration};
use common::assert_approximately_now;
use cotoami_db::prelude::*;
use image::ImageFormat;
use tempfile::tempdir;

pub mod common;

#[test]
fn default_state() -> Result<()> {
    // when
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.new_session()?;

    // then
    assert_eq!(session.local_node()?, None);
    assert!(session.all_nodes()?.is_empty());

    Ok(())
}

#[test]
fn init_as_empty_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.new_session()?;

    // when
    let ((local_node, node), changelog) = session.init_as_node(None, None)?;

    // then
    assert_matches!(
        node,
        Node {
            rowid: 1,
            ref name,
            root_cotonoma_id: None,
            version: 1,
            ..
        } if name == ""
    );
    assert_icon_generated(&node)?;
    assert_approximately_now(node.created_at());

    assert_matches!(
        local_node,
        LocalNode {
            node_id,
            rowid: 1,
            owner_password_hash: None,
            owner_session_token: None,
            owner_session_expires_at: None,
            ..
        } if node_id == node.uuid
    );

    let operator = session.local_node_as_operator()?;
    assert_matches!(
        session.local_node_pair(&operator)?,
        (a, b) if a == local_node && b == node
    );
    assert_eq!(session.node(&node.uuid)?.unwrap(), node);
    assert_matches!(&session.all_nodes()?[..], [a] if a == &node);

    assert_matches!(
        changelog,
        ChangelogEntry {
            serial_number: 1,
            origin_node_id,
            origin_serial_number: 1,
            change: Change::CreateNode(a, None),
            ..
        } if origin_node_id == node.uuid &&
             a == Node { rowid: 0, ..node }
    );

    Ok(())
}

#[test]
fn duplicate_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let session = db.new_session()?;
    let _ = session.init_as_node(None, None)?;

    // when
    let result = session.init_as_node(None, None);

    // then
    assert_eq!(
        result.unwrap_err().to_string(),
        "UNIQUE constraint failed: local_node.rowid"
    );
    Ok(())
}

#[test]
fn owner_session() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let session = db.new_session()?;
    let duration = Duration::minutes(30);

    // when
    let ((mut local_node, _), _) = session.init_as_node(None, Some("foo"))?;

    // then
    assert!(local_node.start_session("bar", duration).is_err());

    let session_id = local_node.start_session("foo", duration)?.to_owned();
    assert_eq!(
        local_node.owner_session_token.as_deref().unwrap(),
        &session_id
    );
    assert_approximately_now(local_node.session_expires_at_as_local_time().unwrap() - duration);
    local_node.verify_session(&session_id)?;
    assert_eq!(
        local_node
            .verify_session("invalid-token")
            .unwrap_err()
            .to_string(),
        "The passed session token is invalid."
    );

    // when
    local_node.owner_session_expires_at = Some(Utc::now().naive_utc() - Duration::seconds(1));

    // then
    assert_eq!(
        local_node
            .verify_session(&session_id)
            .unwrap_err()
            .to_string(),
        "Session has been expired."
    );

    // when
    local_node.clear_session();

    // then
    assert_eq!(
        local_node
            .verify_session(&session_id)
            .unwrap_err()
            .to_string(),
        "Session doesn't exist."
    );

    Ok(())
}

#[test]
fn init_as_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.new_session()?;

    // when
    let ((local_node, node), changelog) = session.init_as_node(Some("My Node"), None)?;

    // then
    let (root_cotonoma, root_coto) = session.root_cotonoma()?.unwrap();

    assert_matches!(
        node,
        Node {
            rowid: 1,
            ref name,
            version: 2,  // root_cotonoma_id has been updated
            ..
        } if name == "My Node"
    );
    assert_icon_generated(&node)?;
    assert_approximately_now(node.created_at());

    assert_matches!(
        local_node,
        LocalNode {
            node_id,
            rowid: 1,
            owner_password_hash: None,
            owner_session_token: None,
            owner_session_expires_at: None,
            ..
        } if node_id == node.uuid
    );

    let operator = session.local_node_as_operator()?;
    assert_matches!(
        session.local_node_pair(&operator)?,
        (a, b) if a == local_node && b == node
    );
    assert_eq!(session.node(&node.uuid)?.unwrap(), node);
    assert_matches!(&session.all_nodes()?[..], [a] if a == &node);

    assert_matches!(
        root_cotonoma,
        Cotonoma {
            uuid,
            node_id,
            coto_id,
            ref name,
            ..
        } if Some(uuid) == node.root_cotonoma_id &&
             node_id == node.uuid &&
             coto_id == root_coto.uuid &&
             name == "My Node"
    );
    assert_approximately_now(root_cotonoma.created_at());
    assert_approximately_now(root_cotonoma.updated_at());
    assert_matches!(&session.all_cotonomas()?[..], [a] if a == &root_cotonoma);

    assert_matches!(
        root_coto,
        Coto {
            node_id,
            posted_in_id: None,
            posted_by_id,
            content: None,
            summary: Some(ref summary),
            is_cotonoma: true,
            repost_of_id: None,
            reposted_in_ids: None,
            ..
        } if node_id == node.uuid &&
             posted_by_id == node.uuid &&
             summary == "My Node"
    );
    assert_approximately_now(root_coto.created_at());
    assert_approximately_now(root_coto.updated_at());
    assert_matches!(&session.all_cotos()?[..], [a] if a == &root_coto);

    assert_matches!(
        changelog,
        ChangelogEntry {
            serial_number: 1,
            origin_node_id,
            origin_serial_number: 1,
            change: Change::CreateNode(a, Some((b, c))),
            ..
        } if origin_node_id == node.uuid &&
             a == Node { rowid: 0, ..node } &&
             b == root_cotonoma &&
             c == Coto { rowid: 0, ..root_coto }
    );

    Ok(())
}

fn assert_icon_generated(node: &Node) -> Result<()> {
    let image = image::load_from_memory_with_format(&node.icon, ImageFormat::Png)?;
    assert_eq!(image.width(), 600);
    assert_eq!(image.height(), 600);
    Ok(())
}
