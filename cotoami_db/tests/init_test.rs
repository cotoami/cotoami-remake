use anyhow::Result;
use assert_matches::assert_matches;
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
    assert_eq!(session.local_node()?, None);
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
    let (local_node, node) = session.init_as_empty_node(None)?;

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
    common::assert_approximately_now(&node.created_at());
    common::assert_approximately_now(&node.inserted_at());

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

    assert_eq!(
        session.local_node()?,
        Some((local_node.clone(), node.clone()))
    );
    assert_eq!(session.get_node(&node.uuid)?.unwrap(), node);
    assert_eq!(session.all_nodes()?, vec![node]);

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
fn owner_password() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    // when
    let (local_node, _) = session.init_as_empty_node(Some("foo"))?;

    // then
    assert!(local_node.verify_owner_password("foo").is_ok());
    assert!(local_node.verify_owner_password("bar").is_err());
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
    assert_matches!(
        node,
        Node {
            rowid: 1,
            ref name,
            root_cotonoma_id,
            version: 2,  // root_cotonoma_id has been updated
            ..
        } if name == "My Node" &&
             root_cotonoma_id.is_some()
    );
    assert_icon_generated(&node)?;
    common::assert_approximately_now(&node.created_at());
    common::assert_approximately_now(&node.inserted_at());

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

    assert_eq!(
        session.local_node()?,
        Some((local_node.clone(), node.clone()))
    );
    assert_eq!(session.get_node(&node.uuid)?.unwrap(), node);
    assert_eq!(session.all_nodes()?, vec![node.clone()]);

    let (root_cotonoma, root_coto) = session
        .get_cotonoma(&node.root_cotonoma_id.unwrap())?
        .unwrap();

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
    common::assert_approximately_now(&root_cotonoma.created_at());
    common::assert_approximately_now(&root_cotonoma.updated_at());

    let all_cotonomas = session.all_cotonomas()?;
    assert_eq!(all_cotonomas.len(), 1);
    assert_eq!(all_cotonomas[0], root_cotonoma);

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
    common::assert_approximately_now(&root_coto.created_at());
    common::assert_approximately_now(&root_coto.updated_at());

    let all_cotos = session.all_cotos()?;
    assert_eq!(all_cotos.len(), 1);
    assert_eq!(all_cotos[0], root_coto);

    assert_matches!(
        changelog,
        ChangelogEntry {
            serial_number: 1,
            parent_node_id: None,
            parent_serial_number: None,
            change: Change::CreateCotonoma(cotonoma, coto),
            ..
        } if cotonoma == all_cotonomas[0] &&
             coto == Coto { rowid: 0, ..all_cotos[0].clone() }
    );

    Ok(())
}

fn assert_icon_generated(node: &Node) -> Result<()> {
    let image = image::load_from_memory_with_format(&node.icon, ImageFormat::Png)?;
    assert_eq!(image.width(), 600);
    assert_eq!(image.height(), 600);
    Ok(())
}
