use anyhow::Result;
use cotoami_db::ChildNodeInput;

pub mod common;

#[test]
fn parents() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_sun_dir, sun_db, _sun_node) = common::setup_db("Sun")?;
    let (_moon_dir, moon_db, _moon_node) = common::setup_db("Moon")?;
    let (_earth_dir, earth_db, _earth_node) = common::setup_db("Earth")?;

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
        "http://sun",
        "sun-earth-password",
        ChildNodeInput::as_owner(),
    )?;

    /////////////////////////////////////////////////////////////////////////////
    // others_last_posted_at
    /////////////////////////////////////////////////////////////////////////////

    Ok(())
}
