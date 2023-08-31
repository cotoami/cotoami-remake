use anyhow::Result;
use assert_matches::assert_matches;
use cotoami_db::prelude::*;
use tempfile::tempdir;

pub mod common;

#[test]
fn import_changes() -> Result<()> {
    // setup: db1
    let db1_dir = tempdir()?;
    let db1 = Database::new(&db1_dir)?;
    let mut session1 = db1.create_session()?;

    let ((_, node1), db1_change1) = session1.init_as_node(Some("My Node"), None)?;
    let operator1 = session1.local_node_as_operator()?;
    let (node1_root_cotonoma, node1_root_coto) = session1.get_root_cotonoma()?.unwrap();

    let (db1_coto, db1_change2) =
        session1.post_coto("hello", None, &node1_root_cotonoma, &operator1)?;
    let (db1_edited_coto, db1_change3) =
        session1.edit_coto(&db1_coto.uuid, "bar", Some("foo"), &operator1)?;
    let db1_change4 = session1.delete_coto(&db1_coto.uuid, &operator1)?;

    // setup: db2
    let db2_dir = tempdir()?;
    let db2 = Database::new(&db2_dir)?;
    let mut session2 = db2.create_session()?;

    let ((_, _node2), _db2_change1) = session2.init_as_node(None, None)?;
    let operator2 = session2.local_node_as_operator()?;

    let Some((_, _db2_change2)) = session2.import_node(&node1)? else { panic!() };
    let parent = session2.put_parent_node(&node1.uuid, "https://node1", &operator2)?;
    assert_eq!(parent.changes_received, 0);

    // when: import change1 (init_as_node)
    let db2_change3 = session2.import_change(&via_serialization(&db1_change1)?, &node1.uuid)?;

    // then
    assert_eq!(
        session2
            .get_parent_node(&node1.uuid)
            .unwrap()
            .changes_received,
        1
    );
    assert_matches!(
        db2_change3,
        ChangelogEntry {
            serial_number: 3,
            origin_node_id,
            origin_serial_number,
            change,
            ..
        } if origin_node_id == node1.uuid &&
             origin_serial_number == db1_change1.origin_serial_number &&
             change == db1_change1.change
    );

    assert_eq!(
        session2.get_node(&node1.uuid)?.unwrap(),
        Node {
            rowid: 2, // rowid=1 is db2's local node
            ..node1.clone()
        }
    );
    let (cotonoma, coto) = session2.get_cotonoma(&node1_root_cotonoma.uuid)?.unwrap();
    assert_eq!(cotonoma, node1_root_cotonoma);
    assert_eq!(coto, node1_root_coto);
    assert_matches!(
        &session2.all_cotonomas()?[..],
        [a] if a == &node1_root_cotonoma
    );
    assert_matches!(
        &session2.all_cotos()?[..],
        [a] if a == &node1_root_coto
    );

    // when: import change2 (post_coto)
    let db2_change4 = session2.import_change(&via_serialization(&db1_change2)?, &node1.uuid)?;

    // then
    assert_eq!(
        session2
            .get_parent_node(&node1.uuid)
            .unwrap()
            .changes_received,
        2
    );
    assert_matches!(
        db2_change4,
        ChangelogEntry {
            serial_number: 4,
            origin_node_id,
            origin_serial_number,
            change,
            ..
        } if origin_node_id == node1.uuid &&
             origin_serial_number == db1_change2.origin_serial_number &&
             change == db1_change2.change
    );
    assert_matches!(
        &session2.all_cotos()?[..],
        [a, b] if a == &node1_root_coto &&
                  b == &db1_coto
    );

    // when: import change3 (edit_coto)
    let db2_change5 = session2.import_change(&via_serialization(&db1_change3)?, &node1.uuid)?;

    // then
    assert_eq!(
        session2
            .get_parent_node(&node1.uuid)
            .unwrap()
            .changes_received,
        3
    );
    assert_matches!(
        db2_change5,
        ChangelogEntry {
            serial_number: 5,
            origin_node_id,
            origin_serial_number,
            change,
            ..
        } if origin_node_id == node1.uuid &&
             origin_serial_number == db1_change3.origin_serial_number &&
             change == db1_change3.change
    );
    assert_matches!(
        &session2.all_cotos()?[..],
        [a, b] if a == &node1_root_coto &&
                  b == &db1_edited_coto
    );

    // when: import change4 (delete_coto)
    let db2_change6 = session2.import_change(&via_serialization(&db1_change4)?, &node1.uuid)?;

    // then
    assert_eq!(
        session2
            .get_parent_node(&node1.uuid)
            .unwrap()
            .changes_received,
        4
    );
    assert_matches!(
        db2_change6,
        ChangelogEntry {
            serial_number: 6,
            origin_node_id,
            origin_serial_number,
            change,
            ..
        } if origin_node_id == node1.uuid &&
             origin_serial_number == db1_change4.origin_serial_number &&
             change == db1_change4.change
    );
    assert_matches!(
        &session2.all_cotos()?[..],
        [a] if a == &node1_root_coto
    );

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
