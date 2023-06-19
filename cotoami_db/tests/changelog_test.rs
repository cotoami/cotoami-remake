use anyhow::Result;
use cotoami_db::prelude::*;
use tempfile::tempdir;

pub mod common;

#[test]
fn import_changes() -> Result<()> {
    // setup
    let db1_dir = tempdir()?;
    let db1 = Database::new(&db1_dir)?;
    let mut session1 = db1.create_session()?;

    let (node, db1_change1) = session1.init_as_node("My Node", None)?;
    let cotonoma_id = node.root_cotonoma_id.unwrap();

    let (coto, db1_change2) = session1.post_coto(&cotonoma_id, None, "hello", None)?;
    let (edited_coto, db1_change3) = session1.edit_coto(&coto.uuid, "bar", Some("foo"))?;
    let db1_change4 = session1.delete_coto(&coto.uuid)?;

    let db2_dir = tempdir()?;
    let db2 = Database::new(&db2_dir)?;
    let mut session2 = db2.create_session()?;

    // when: import change1
    let db2_change1 = session2.import_change(&node.uuid, &db1_change1)?;

    // then
    assert_eq!(db2_change1, db1_change1);

    Ok(())
}
