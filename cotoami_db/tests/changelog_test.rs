use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::prelude::*;
use tempfile::tempdir;

pub mod common;

#[test]
fn import_changes() -> Result<()> {
    // setup
    let db1_dir = tempdir()?;
    let db1 = Database::new(&db1_dir)?;
    let mut session1 = db1.create_session()?;

    let (node1, db1_change1) = session1.init_as_node("My Node", None)?;
    let root_cotonoma_id = node1.root_cotonoma_id.unwrap();
    let (root_cotonoma, root_coto) = session1.get_cotonoma(&root_cotonoma_id)?.unwrap();

    let (coto, db1_change2) = session1.post_coto(&root_cotonoma_id, None, "hello", None)?;
    let (_, db1_change3) = session1.edit_coto(&coto.uuid, "bar", Some("foo"))?;
    let db1_change4 = session1.delete_coto(&coto.uuid)?;

    let db2_dir = tempdir()?;
    let db2 = Database::new(&db2_dir)?;
    let mut session2 = db2.create_session()?;
    session2.init_as_empty_node(None)?;

    let db1_nodes = session1.all_nodes()?;
    session2.import_nodes(&through_serialization(&db1_nodes)?)?;

    // when: import change1
    let db2_change1 = session2.import_change(&node1.uuid, &through_serialization(&db1_change1)?)?;

    // then
    assert_matches!(
        db2_change1,
        ChangelogEntry {
            serial_number: 1,
            parent_node_id,
            parent_serial_number,
            change,
            ..
        } if parent_node_id == Some(node1.uuid) &&
             parent_serial_number == Some(db1_change1.serial_number) &&
             change == db1_change1.change
    );

    let (cotonoma, coto) = session2.get_cotonoma(&root_cotonoma_id)?.unwrap();
    assert_eq!(cotonoma, root_cotonoma);
    assert_eq!(coto, root_coto);
    assert_eq!(session2.recent_cotonomas(None, 5, 0)?.total_rows, 1);
    assert_eq!(session2.recent_cotos(None, None, 5, 0)?.total_rows, 1);

    Ok(())
}

fn through_serialization<T>(log: &T) -> Result<T>
where
    T: serde::Serialize + serde::de::DeserializeOwned,
{
    let json_string = serde_json::to_string(log)?;
    let deserialized = serde_json::from_str(&json_string)?;
    Ok(deserialized)
}
