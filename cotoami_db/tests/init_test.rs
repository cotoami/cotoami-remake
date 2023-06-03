use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::{Database, Node};
use image::ImageFormat;
use tempfile::tempdir;

mod common;

#[test]
fn default_state() -> Result<()> {
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    assert_eq!(session.as_node()?, None);
    assert!(session.all_nodes()?.is_empty());

    Ok(())
}

#[test]
fn init_as_node() -> Result<()> {
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    let node = session.init_as_node("My Database", None)?;
    assert_matches!(
        node,
        Node {
            rowid: 1,
            ref name,
            root_cotonoma_id: None,
            owner_password_hash: None,
            version: 1,
            ..
        } if name == "My Database"
    );
    common::assert_approximately_now(&node.created_at());
    common::assert_approximately_now(&node.inserted_at());

    // node.icon should be generated
    let image = image::load_from_memory_with_format(&node.icon, ImageFormat::Png)?;
    assert_eq!(image.width(), 600);
    assert_eq!(image.height(), 600);

    // Asserting the self node row has been inserted
    assert_eq!(session.get_node(&node.uuid)?.unwrap(), node);
    assert_eq!(session.all_nodes()?, vec![node]);

    Ok(())
}
