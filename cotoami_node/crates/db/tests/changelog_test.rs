use std::str::FromStr;

use anyhow::Result;
use cotoami_db::prelude::*;
use tempfile::tempdir;
pub mod common;
use chrono::offset::Utc;
use googletest::prelude::*;

#[test]
fn import_changes() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup: create changes in db1
    /////////////////////////////////////////////////////////////////////////////

    let db1_dir = tempdir()?;
    let db1 = Database::new(&db1_dir)?;
    let mut ds1 = db1.new_session()?;

    // 1. init_as_node
    let ((_, node1), db1_change1) = ds1.init_as_node(Some("My Node"), None)?;

    let opr1 = db1.globals().local_node_as_operator()?;
    let (node1_root_cotonoma, node1_root_coto) = ds1.local_node_root()?.unwrap();

    // 2. post_coto
    let (db1_coto, db1_change2) =
        ds1.post_coto(&CotoInput::new("hello"), &node1_root_cotonoma.uuid, &opr1)?;

    // 3. edit_coto
    let diff = CotoContentDiff::default()
        .content("bar")
        .summary(Some("foo"));
    let (db1_edited_coto, db1_change3) = ds1.edit_coto(&db1_coto.uuid, diff, &opr1)?;

    // 4. delete_coto
    let db1_change4 = ds1.delete_coto(&db1_coto.uuid, &opr1)?;

    // 5. post_cotonoma
    let ((db1_cotonoma, db1_cotonoma_coto), db1_change5) =
        ds1.post_cotonoma(&CotonomaInput::new("sun"), &node1_root_cotonoma, &opr1)?;

    // 6. rename_cotonoma
    let ((db1_cotonoma2, db1_cotonoma_coto2), db1_change6) =
        ds1.rename_cotonoma(&db1_cotonoma.uuid, "sunsun", &opr1)?;

    /////////////////////////////////////////////////////////////////////////////
    // Setup: prepare db2 to accept changes from db1
    /////////////////////////////////////////////////////////////////////////////

    let db2_dir = tempdir()?;
    let db2 = Database::new(&db2_dir)?;
    let mut ds2 = db2.new_session()?;

    let ((_, _node2), _) = ds2.init_as_node(None, None)?;
    let opr2 = db2.globals().local_node_as_operator()?;

    let Some(_) = ds2.import_node(&node1)? else { panic!() };
    let (_, parent) = ds2.register_server_node_as_parent(&node1.uuid, "https://node1", &opr2)?;
    assert_eq!(parent.changes_received, 0);

    let assert_changes_received = |changes: i64| {
        assert_that!(
            db2.new_session().unwrap().parent_node(&node1.uuid, &opr2),
            ok(some(pat!(ParentNode {
                changes_received: eq(&changes)
            })))
        );
    };

    /////////////////////////////////////////////////////////////////////////////
    // When: import change1 (init_as_node)
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds2.import_change(&via_serialization(&db1_change1)?, &node1.uuid)?;

    assert_changes_received(1);
    assert_that!(
        changelog.unwrap(),
        pat!(ChangelogEntry {
            serial_number: eq(&3),
            origin_node_id: eq(&node1.uuid),
            origin_serial_number: eq(&db1_change1.origin_serial_number),
            change: eq(&db1_change1.change),
            import_error: none(),
        })
    );

    // db2 should have node1.
    assert_that!(
        ds2.node(&node1.uuid)?.unwrap(),
        eq(&Node {
            rowid: 2, // rowid is not necessarily the same
            ..node1.clone()
        })
    );

    // db2 should have node1's root cotonoma/coto
    let (cotonoma, coto) = ds2.cotonoma_pair(&node1_root_cotonoma.uuid)?.unwrap();
    assert_that!(cotonoma, eq(&node1_root_cotonoma));
    assert_that!(coto, eq(&node1_root_coto));

    /////////////////////////////////////////////////////////////////////////////
    // When: import change2 (post_coto)
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds2.import_change(&via_serialization(&db1_change2)?, &node1.uuid)?;

    assert_changes_received(2);
    assert_that!(
        changelog.unwrap(),
        pat!(ChangelogEntry {
            serial_number: eq(&4),
            origin_node_id: eq(&node1.uuid),
            origin_serial_number: eq(&db1_change2.origin_serial_number),
            change: eq(&db1_change2.change),
            import_error: none(),
        })
    );

    // db2 should have the posted coto.
    assert_that!(ds2.coto(&db1_coto.uuid), ok(some(eq(&db1_coto))));

    /////////////////////////////////////////////////////////////////////////////
    // When: import change3 (edit_coto)
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds2.import_change(&via_serialization(&db1_change3)?, &node1.uuid)?;

    assert_changes_received(3);
    assert_that!(
        changelog.unwrap(),
        pat!(ChangelogEntry {
            serial_number: eq(&5),
            origin_node_id: eq(&node1.uuid),
            origin_serial_number: eq(&db1_change3.origin_serial_number),
            change: eq(&db1_change3.change),
            import_error: none(),
        })
    );

    // db2 should have the edited post.
    assert_that!(ds2.coto(&db1_coto.uuid), ok(some(eq(&db1_edited_coto))));

    /////////////////////////////////////////////////////////////////////////////
    // When: import change4 (delete_coto)
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds2.import_change(&via_serialization(&db1_change4)?, &node1.uuid)?;

    assert_changes_received(4);
    assert_that!(
        changelog.unwrap(),
        pat!(ChangelogEntry {
            serial_number: eq(&6),
            origin_node_id: eq(&node1.uuid),
            origin_serial_number: eq(&db1_change4.origin_serial_number),
            change: eq(&db1_change4.change),
            import_error: none(),
        })
    );

    // db2 shouldn't have the deleted coto
    assert_that!(ds2.coto(&db1_coto.uuid), ok(none()));

    /////////////////////////////////////////////////////////////////////////////
    // When: import change5 (post_cotonoma)
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds2.import_change(&via_serialization(&db1_change5)?, &node1.uuid)?;

    assert_changes_received(5);
    assert_that!(
        changelog.unwrap(),
        pat!(ChangelogEntry {
            serial_number: eq(&7),
            origin_node_id: eq(&node1.uuid),
            origin_serial_number: eq(&db1_change5.origin_serial_number),
            change: eq(&db1_change5.change),
            import_error: none(),
        })
    );

    // db2 should have the posted cotonoma.
    assert_that!(
        ds2.cotonoma_pair(&db1_cotonoma.uuid),
        ok(some((eq(&db1_cotonoma), eq(&db1_cotonoma_coto))))
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: import change6 (rename_cotonoma)
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds2.import_change(&via_serialization(&db1_change6)?, &node1.uuid)?;

    assert_changes_received(6);
    assert_that!(
        changelog.unwrap(),
        pat!(ChangelogEntry {
            serial_number: eq(&8),
            origin_node_id: eq(&node1.uuid),
            origin_serial_number: eq(&db1_change6.origin_serial_number),
            change: eq(&db1_change6.change),
            import_error: none(),
        })
    );

    // db2 should have the renamed cotonoma.
    assert_that!(
        ds2.cotonoma_pair(&db1_cotonoma.uuid),
        ok(some((eq(&db1_cotonoma2), eq(&db1_cotonoma_coto2))))
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

#[test]
fn duplicate_changes_from_different_parents() -> Result<()> {
    // setup
    let (_dir1, db1, _) = common::setup_db("Node1")?;
    let (_dir2, _, node2) = common::setup_db("Node2")?;
    let (_dir3, _, node3) = common::setup_db("Node3")?;

    let ds1 = db1.new_session()?;
    let opr = db1.globals().local_node_as_operator()?;
    ds1.import_node(&node2)?;
    ds1.register_server_node_as_parent(&node2.uuid, "https://node2", &opr)?;
    ds1.import_node(&node3)?;
    ds1.register_server_node_as_parent(&node3.uuid, "https://node3", &opr)?;

    let origin_node_id = Id::from_str("00000000-0000-0000-0000-000000000001")?;
    let src_change = ChangelogEntry {
        serial_number: 1,
        origin_node_id,
        origin_serial_number: 1,
        change: Change::None,
        import_error: None,
        inserted_at: Utc::now().naive_utc(),
    };

    let imported_change1 = ds1.import_change(&src_change, &node2.uuid)?;
    assert_that!(
        imported_change1.unwrap(),
        pat!(ChangelogEntry {
            serial_number: eq(&4),
            origin_node_id: eq(&origin_node_id),
            origin_serial_number: eq(&1)
        })
    );
    assert_eq!(
        ds1.parent_node(&node2.uuid, &opr)?
            .unwrap()
            .changes_received,
        1
    );

    // when: the same change from a different parent
    let imported_change2 = ds1.import_change(&src_change, &node3.uuid)?;

    // then
    assert!(imported_change2.is_none());
    assert_eq!(
        ds1.parent_node(&node3.uuid, &opr)?
            .unwrap()
            .changes_received,
        // the change should not be applied,
        // but still the number should be incremented.
        1
    );

    Ok(())
}

#[test]
fn import_error() -> Result<()> {
    let (_dir1, db, _) = common::setup_db("Local")?;
    let (_dir2, _, parent_node) = common::setup_db("Parent")?;

    let ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    ds.import_node(&parent_node)?;
    ds.register_server_node_as_parent(&parent_node.uuid, "https://parent", &opr)?;

    // A change to cause an import error
    let log = ChangelogEntry {
        serial_number: 1,
        origin_node_id: parent_node.uuid,
        origin_serial_number: 1,
        change: Change::RenameNode {
            node_id: Id::generate(), // no such node
            name: "hello".into(),
            updated_at: Utc::now().naive_utc(),
        },
        import_error: None,
        inserted_at: Utc::now().naive_utc(),
    };

    let inserted_log = ds.import_change(&log, &parent_node.uuid)?;
    assert_that!(
        inserted_log,
        some(pat!(ChangelogEntry {
            serial_number: eq(&3),
            origin_node_id: eq(&parent_node.uuid),
            origin_serial_number: eq(&1),
            change: pat!(Change::RenameNode {
                node_id: anything()
            }),
            import_error: some(eq("NotFound"))
        }))
    );

    Ok(())
}
