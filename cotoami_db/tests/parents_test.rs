use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn parents() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_sun_dir, sun_db, sun_node) = common::setup_db("Sun")?;
    let (_moon_dir, moon_db, moon_node) = common::setup_db("Moon")?;
    let (_earth_dir, earth_db, earth_node) = common::setup_db("Earth")?;

    let mut sun_ds = sun_db.new_session()?;
    let (_sun_root_cotonoma, sun_root_coto) = sun_ds.try_get_local_node_root()?;

    let mut moon_ds = moon_db.new_session()?;
    let (_moon_root_cotonoma, moon_root_coto) = moon_ds.try_get_local_node_root()?;

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
    common::connect_parent_child(
        &sun_db,
        &moon_db,
        "http://sun",
        "sun-moon-password",
        ChildNodeInput::as_owner(),
    )?;

    let others_last_posted_at = earth_ds.others_last_posted_at(&earth_opr)?;
    assert_that!(others_last_posted_at.len(), eq(2));
    assert_that!(
        others_last_posted_at,
        has_entry(sun_node.uuid, eq(&sun_root_coto.created_at))
    );
    assert_that!(
        others_last_posted_at,
        has_entry(moon_node.uuid, eq(&moon_root_coto.created_at))
    );

    let unread_counts = earth_ds.unread_counts(&earth_opr)?;
    assert_that!(unread_counts.len(), eq(3));
    assert_that!(unread_counts, has_entry(earth_node.uuid, eq(&0)));
    assert_that!(unread_counts, has_entry(sun_node.uuid, eq(&1)));
    assert_that!(unread_counts, has_entry(moon_node.uuid, eq(&1)));

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

    /////////////////////////////////////////////////////////////////////////////
    // When: mark_as_read
    /////////////////////////////////////////////////////////////////////////////

    let read_at = earth_ds.mark_as_read(&sun_node.uuid, None, &earth_opr)?;
    assert_that!(
        earth_ds.parent_node(&sun_node.uuid, &earth_opr)?,
        some(pat!(ParentNode {
            node_id: eq(&sun_node.uuid),
            last_read_at: some(eq(&read_at)),
        }))
    );

    Ok(())
}
