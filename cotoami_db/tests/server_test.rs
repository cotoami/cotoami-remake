use anyhow::Result;
use cotoami_db::prelude::*;

pub mod common;

#[test]
fn change_server() -> Result<()> {
    // setup
    let (_dir1, db1, _node1) = common::setup_db("Node1")?;
    let (_dir2, _db2, node2) = common::setup_db("Node2")?;

    let mut ds1 = db1.new_session()?;
    let opr = db1.globals().local_node_as_operator()?;

    ds1.import_node(&node2)?;
    ds1.register_server_node_as_parent(&node2.uuid, "https://node2", &opr)?;

    // when
    ds1.save_server_password(&node2.uuid, "node2-password", "master-password", &opr)?;

    // then
    let server = ds1.server_node(&node2.uuid, &opr)?.unwrap();
    assert_eq!(
        server.password("invalid-password").unwrap_err().to_string(),
        "aead::Error"
    );
    assert_eq!(
        server.password("master-password")?,
        Some("node2-password".into())
    );

    // when: disabled = true
    let network_role = ds1.set_network_disabled(&node2.uuid, true, &opr)?;

    // then
    let NetworkRole::Server(server) = network_role else { unreachable!() };
    assert_eq!(server.disabled, true);

    // when: disabled = false
    let network_role = ds1.set_network_disabled(&node2.uuid, false, &opr)?;

    // then
    let NetworkRole::Server(server) = network_role else { unreachable!() };
    assert_eq!(server.disabled, false);

    Ok(())
}
