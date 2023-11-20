use anyhow::Result;

pub mod common;

#[test]
fn save_password() -> Result<()> {
    // setup
    let (_dir1, db1, _node1) = common::setup_db("Node1")?;
    let (_dir2, _db2, node2) = common::setup_db("Node2")?;

    let mut session1 = db1.new_session()?;
    let opr = session1.local_node_as_operator()?;

    session1.import_node(&node2)?;
    session1.register_server_node_as_parent(&node2.uuid, "https://node2", &opr)?;

    // when
    session1.save_server_password(&node2.uuid, "node2-password", "master-password", &opr)?;

    // then
    let server = session1.server_node(&node2.uuid, &opr)?.unwrap();
    assert_eq!(
        server.password("invalid-password").unwrap_err().to_string(),
        "aead::Error"
    );
    assert_eq!(
        server.password("master-password")?,
        Some("node2-password".into())
    );

    Ok(())
}
