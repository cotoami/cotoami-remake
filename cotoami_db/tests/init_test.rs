use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::{Change, ChangelogEntry, Coto, Cotonoma, Database, Node};
use image::ImageFormat;
use tempfile::tempdir;

mod common;

#[test]
fn default_state() -> Result<()> {
    // when
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    // then
    assert_eq!(session.as_node()?, None);
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
    let node = session.init_as_empty_node(None)?;

    // then
    assert_matches!(
        node,
        Node {
            rowid: 1,
            ref name,
            root_cotonoma_id: None,
            owner_password_hash: None,
            version: 1,
            ..
        } if name == ""
    );
    assert_icon_generated(&node)?;
    common::assert_approximately_now(&node.created_at());
    common::assert_approximately_now(&node.inserted_at());

    assert_eq!(session.as_node()?, Some(node.clone()));
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
        "UNIQUE constraint failed: nodes.rowid"
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
    let node = session.init_as_empty_node(Some("foo"))?;

    // then
    assert!(node.verify_owner_password("foo").is_ok());
    assert!(node.verify_owner_password("bar").is_err());
    Ok(())
}

#[test]
fn init_as_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    // when
    let (node, changelog) = session.init_as_node("My Node", None)?;

    // then
    assert_matches!(
        node,
        Node {
            rowid: 1,
            ref name,
            // root_cotonoma_id: None,
            owner_password_hash: None,
            version: 2,  // upgraded once with root_cotonoma_id
            ..
        } if name == "My Node"
    );
    assert_icon_generated(&node)?;
    common::assert_approximately_now(&node.created_at());
    common::assert_approximately_now(&node.inserted_at());

    assert_eq!(session.as_node()?, Some(node.clone()));
    assert_eq!(session.get_node(&node.uuid)?.unwrap(), node);
    assert_eq!(session.all_nodes()?, vec![node.clone()]);

    let all_cotonomas = session.recent_cotonomas(None, 5, 0)?;
    assert_eq!(all_cotonomas.rows.len(), 1);
    assert_matches!(
        all_cotonomas.rows[0],
        Cotonoma {
            node_id,
            ref name,
            ..
        } if node_id == node.uuid &&
             name == "My Node"
    );
    common::assert_approximately_now(&all_cotonomas.rows[0].created_at());
    common::assert_approximately_now(&all_cotonomas.rows[0].updated_at());

    let all_cotos = session.recent_cotos(None, None, 5, 0)?;
    assert_eq!(all_cotos.rows.len(), 1);
    assert_matches!(
        all_cotos.rows[0],
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
    common::assert_approximately_now(&all_cotos.rows[0].created_at());
    common::assert_approximately_now(&all_cotos.rows[0].updated_at());

    assert_matches!(
        changelog,
        ChangelogEntry {
            serial_number: 1,
            parent_node_id: None,
            parent_serial_number: None,
            change: Change::CreateCotonoma(cotonoma, coto),
            ..
        } if cotonoma == all_cotonomas.rows[0] &&
             coto == Coto { rowid: 0, ..all_cotos.rows[0].clone() }
    );

    Ok(())
}

fn assert_icon_generated(node: &Node) -> Result<()> {
    let image = image::load_from_memory_with_format(&node.icon, ImageFormat::Png)?;
    assert_eq!(image.width(), 600);
    assert_eq!(image.height(), 600);
    Ok(())
}
