use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn parents() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_sun_dir, sun_db, _sun_node) = common::setup_db("Sun")?;
    let (_moon_dir, moon_db, _moon_node) = common::setup_db("Moon")?;
    let (_earth_dir, earth_db, _earth_node) = common::setup_db("Earth")?;

    let mut earth_ds = earth_db.new_session()?;
    let earth_opr = earth_db.globals().local_node_as_operator()?;

    common::connect_parent_child(
        &sun_db,
        &earth_db,
        "http://sun",
        "sun-earth-password",
        ChildNodeInput::as_owner(),
    )?;
    common::connect_parent_child(
        &moon_db,
        &earth_db,
        "http://moon",
        "moon-earth-password",
        ChildNodeInput::as_owner(),
    )?;

    /////////////////////////////////////////////////////////////////////////////
    // When: parent_nodes
    /////////////////////////////////////////////////////////////////////////////

    let parent_nodes = earth_ds.parent_nodes(&earth_opr)?;
    assert_that!(
        parent_nodes,
        elements_are![
            pat!(ParentNode {
                node_id: eq(&moon_db.globals().try_get_local_node_id()?),
            }),
            pat!(ParentNode {
                node_id: eq(&sun_db.globals().try_get_local_node_id()?)
            })
        ]
    );

    Ok(())
}
