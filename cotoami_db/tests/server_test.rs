use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn update_server() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_sun_dir, _sun_db, sun_node) = common::setup_db("Sun")?;
    let (_earth_dir, earth_db, _earth_node) =
        common::setup_db_with_password("Earth", Some("earth-password"))?;
    let mut earth_ds = earth_db.new_session()?;
    let earth_opr = earth_db.globals().local_node_as_operator()?;

    earth_ds.import_node(&sun_node)?;
    let (server, _parent) = earth_ds.register_server_node_as_parent(
        &sun_node.uuid,
        "https://sun-example.com",
        &earth_opr,
    )?;

    /////////////////////////////////////////////////////////////////////////////
    // When: save_server_password with invalid owner password
    /////////////////////////////////////////////////////////////////////////////

    assert_that!(
        earth_ds.save_server_password(
            &server.node_id,
            "sun-as-server-password",
            "invalid-password",
            &earth_opr,
        ),
        err(displays_as(eq("Authentication failed")))
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: save_server_password
    /////////////////////////////////////////////////////////////////////////////

    let server = earth_ds.save_server_password(
        &server.node_id,
        "sun-as-server-password",
        "earth-password",
        &earth_opr,
    )?;

    assert_that!(
        server.password("invalid-password"),
        err(displays_as(eq("Invalid owner password.")))
    );
    assert_that!(
        server.password("earth-password")?,
        some(eq("sun-as-server-password"))
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: disable
    /////////////////////////////////////////////////////////////////////////////

    let network_role = earth_ds.set_network_disabled(&sun_node.uuid, true, &earth_opr)?;

    let NetworkRole::Server(server) = network_role else { unreachable!() };
    assert_eq!(server.disabled, true);

    /////////////////////////////////////////////////////////////////////////////
    // When: enable
    /////////////////////////////////////////////////////////////////////////////

    let network_role = earth_ds.set_network_disabled(&sun_node.uuid, false, &earth_opr)?;

    let NetworkRole::Server(server) = network_role else { unreachable!() };
    assert_eq!(server.disabled, false);

    /////////////////////////////////////////////////////////////////////////////
    // When: change_owner_password
    /////////////////////////////////////////////////////////////////////////////

    earth_ds.change_owner_password("earth-password2", "earth-password")?;

    earth_db
        .globals()
        .try_read_local_node()?
        .as_principal()
        .verify_password("earth-password2")?;

    let server = earth_ds.server_node(&server.node_id, &earth_opr)?.unwrap();
    assert_that!(
        server.password("earth-password"),
        err(displays_as(eq("Invalid owner password.")))
    );
    assert_that!(
        server.password("earth-password2")?,
        some(eq("sun-as-server-password"))
    );

    Ok(())
}
