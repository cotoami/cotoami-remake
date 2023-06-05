use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::{Database, Node};
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
    common::assert_approximately_now(&node.created_at());
    common::assert_approximately_now(&node.inserted_at());

    let image = image::load_from_memory_with_format(&node.icon, ImageFormat::Png)?;
    assert_eq!(image.width(), 600);
    assert_eq!(image.height(), 600);

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
