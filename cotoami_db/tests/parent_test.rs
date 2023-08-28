use anyhow::Result;

pub mod common;

#[test]
fn save_password() -> Result<()> {
    // setup
    let (_dir1, db1, _node1) = common::setup_db("Node1")?;
    let (_dir2, _db2, node2) = common::setup_db("Node2")?;

    let mut session1 = db1.create_session()?;
    let operator = session1.local_node_as_operator()?;

    session1.import_node(&node2)?;
    session1.add_parent_node(&node2.uuid, "https://node2", &operator)?;

    // when
    session1.save_parent_node_password(
        &node2.uuid,
        "node2-password",
        "master-password",
        &operator,
    )?;

    // then
    let parent_node = session1.get_parent_node(&node2.uuid)?;
    assert_eq!(
        parent_node
            .password("invalid-password")
            .unwrap_err()
            .to_string(),
        "aead::Error"
    );
    assert_eq!(
        parent_node.password("master-password")?,
        Some("node2-password".into())
    );

    Ok(())
}
