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
    let node1_root_cotonoma_id = node1.root_cotonoma_id.unwrap();
    let (node1_root_cotonoma, node1_root_coto) =
        session1.get_cotonoma(&node1_root_cotonoma_id)?.unwrap();

    let (db1_coto, db1_change2) =
        session1.post_coto(&node1_root_cotonoma_id, None, "hello", None)?;
    let (db1_edited_coto, db1_change3) = session1.edit_coto(&db1_coto.uuid, "bar", Some("foo"))?;
    let db1_change4 = session1.delete_coto(&db1_coto.uuid)?;

    let db2_dir = tempdir()?;
    let db2 = Database::new(&db2_dir)?;
    let mut session2 = db2.create_session()?;
    session2.init_as_empty_node(None)?;

    session2.import_nodes(&via_serialization(&session1.all_nodes()?)?)?;

    // when: import change1 (init_as_node)
    let db2_change1 = session2.import_change(&node1.uuid, &via_serialization(&db1_change1)?)?;

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

    let (cotonoma, coto) = session2.get_cotonoma(&node1_root_cotonoma_id)?.unwrap();
    assert_eq!(cotonoma, node1_root_cotonoma);
    assert_eq!(coto, node1_root_coto);
    assert_eq!(session2.all_cotonomas()?, vec![node1_root_cotonoma.clone()]);
    assert_eq!(session2.all_cotos()?, vec![node1_root_coto.clone()]);

    // when: import change2 (post_coto)
    let db2_change2 = session2.import_change(&node1.uuid, &via_serialization(&db1_change2)?)?;

    // then
    assert_matches!(
        db2_change2,
        ChangelogEntry {
            serial_number: 2,
            parent_node_id,
            parent_serial_number,
            change,
            ..
        } if parent_node_id == Some(node1.uuid) &&
             parent_serial_number == Some(db1_change2.serial_number) &&
             change == db1_change2.change
    );
    assert_eq!(
        session2.all_cotos()?,
        vec![node1_root_coto.clone(), db1_coto.clone()]
    );

    // when: import change3 (edit_coto)
    let db2_change3 = session2.import_change(&node1.uuid, &via_serialization(&db1_change3)?)?;

    // then
    assert_matches!(
        db2_change3,
        ChangelogEntry {
            serial_number: 3,
            parent_node_id,
            parent_serial_number,
            change,
            ..
        } if parent_node_id == Some(node1.uuid) &&
             parent_serial_number == Some(db1_change3.serial_number) &&
             change == db1_change3.change
    );
    assert_eq!(
        session2.all_cotos()?,
        vec![node1_root_coto.clone(), db1_edited_coto.clone()]
    );

    // when: import change4 (delete_coto)
    let db2_change4 = session2.import_change(&node1.uuid, &via_serialization(&db1_change4)?)?;

    // then
    assert_matches!(
        db2_change4,
        ChangelogEntry {
            serial_number: 4,
            parent_node_id,
            parent_serial_number,
            change,
            ..
        } if parent_node_id == Some(node1.uuid) &&
             parent_serial_number == Some(db1_change4.serial_number) &&
             change == db1_change4.change
    );
    assert_eq!(session2.all_cotos()?, vec![node1_root_coto.clone()]);

    Ok(())
}

fn via_serialization<T>(log: &T) -> Result<T>
where
    T: serde::Serialize + serde::de::DeserializeOwned,
{
    let json_string = serde_json::to_string(log)?;
    let deserialized = serde_json::from_str(&json_string)?;
    Ok(deserialized)
}
