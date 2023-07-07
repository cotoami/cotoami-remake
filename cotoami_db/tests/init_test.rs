use anyhow::Result;
use assert_matches::assert_matches;
use chrono::offset::Utc;
use chrono::Duration;
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
    let mut session = db.create_session()?;

    // then
    assert_eq!(session.get_local_node()?, None);
    assert!(session.all_nodes()?.is_empty());

    Ok(())
}

#[test]
fn init_as_empty_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    // when
    let ((local_node, node), changelog) = session.init_as_empty_node(None)?;

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
            owner_session_key: None,
            owner_session_expires_at: None,
            ..
        } if node_id == node.uuid
    );

    assert_matches!(
        session.get_local_node()?,
        Some((a, b)) if a == local_node && b == node
    );
    assert_eq!(session.get_node(&node.uuid)?.unwrap(), node);
    assert_matches!(&session.all_nodes()?[..], [a] if a == &node);

    assert_matches!(
        changelog,
        ChangelogEntry {
            serial_number: 1,
            parent_node_id: None,
            parent_serial_number: None,
            change: Change::ImportNode(a),
            ..
        } if a == Node { rowid: 0, ..node }
    );

    Ok(())
}

#[test]
fn duplicate_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;
    session.init_as_empty_node(None)?;

    // when
    let result = session.init_as_empty_node(None);

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
    let mut session = db.create_session()?;
    let duration = Duration::minutes(30);

    // when
    let ((mut local_node, _), _) = session.init_as_empty_node(Some("foo"))?;

    // then
    assert!(local_node.start_owner_session("bar", duration).is_err());

    let key = local_node.start_owner_session("foo", duration)?.to_owned();
    assert_eq!(local_node.owner_session_key.as_deref().unwrap(), &key);
    assert_approximately_now(local_node.owner_session_expires_at().unwrap() - duration);
    local_node.verify_owner_session(&key)?;
    assert_eq!(
        local_node
            .verify_owner_session("nosuchkey")
            .unwrap_err()
            .to_string(),
        "The passed session key is invalid."
    );

    // when
    local_node.owner_session_expires_at = Some(Utc::now().naive_utc() - Duration::seconds(1));

    // then
    assert_eq!(
        local_node
            .verify_owner_session(&key)
            .unwrap_err()
            .to_string(),
        "Owner session has been expired."
    );

    // when
    local_node.clear_owner_session();

    // then
    assert_eq!(
        local_node
            .verify_owner_session(&key)
            .unwrap_err()
            .to_string(),
        "Owner session doesn't exist."
    );

    Ok(())
}

#[test]
fn init_as_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    // when
    let ((local_node, node), changelog) = session.init_as_node("My Node", None)?;

    // then
    let root_cotonoma_id = node.root_cotonoma_id.unwrap();
    let (root_cotonoma, root_coto) = session.get_cotonoma(&root_cotonoma_id)?.unwrap();

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
            owner_session_key: None,
            owner_session_expires_at: None,
            ..
        } if node_id == node.uuid
    );

    assert_matches!(
        session.get_local_node()?,
        Some((a, b)) if a == local_node && b == node
    );
    assert_eq!(session.get_node(&node.uuid)?.unwrap(), node);
    assert_matches!(&session.all_nodes()?[..], [a] if a == &node);

    assert_matches!(
        root_cotonoma,
        Cotonoma {
            uuid,
            node_id,
            coto_id,
            ref name,
            ..
        } if uuid == root_cotonoma_id &&
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
            parent_node_id: None,
            parent_serial_number: None,
            change: Change::InitNode(a, b, c),
            ..
        } if a == Node { rowid: 0, ..node } &&
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
