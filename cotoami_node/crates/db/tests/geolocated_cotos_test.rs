use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn geolocated_cotos_in_scope() -> Result<()> {
    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root, _) = ds.local_node_root()?.unwrap();

    let ((child1, _), _) = ds.post_cotonoma(&CotonomaInput::new("Geo Child1"), &root, &opr)?;
    let ((child2, _), _) = ds.post_cotonoma(&CotonomaInput::new("Geo Child2"), &child1, &opr)?;
    let ((child3, _), _) = ds.post_cotonoma(&CotonomaInput::new("Geo Child3"), &child2, &opr)?;

    let geolocation = Geolocation::from_lng_lat((135.0, 35.0));
    let (root_coto, _) = ds.post_coto(
        &CotoInput::new("Geo Root").geolocation(geolocation.clone()),
        &root.uuid,
        &opr,
    )?;
    let (child1_coto, _) = ds.post_coto(
        &CotoInput::new("Geo Child1").geolocation(geolocation.clone()),
        &child1.uuid,
        &opr,
    )?;
    let (child2_coto, _) = ds.post_coto(
        &CotoInput::new("Geo Child2").geolocation(geolocation.clone()),
        &child2.uuid,
        &opr,
    )?;
    let (child3_coto, _) = ds.post_coto(
        &CotoInput::new("Geo Child3").geolocation(geolocation),
        &child3.uuid,
        &opr,
    )?;
    let _ = ds.post_coto(&CotoInput::new("No Geo"), &child1.uuid, &opr)?;

    assert_geolocated_in_scope(
        &mut ds,
        Scope::All,
        vec![&root_coto, &child1_coto, &child2_coto, &child3_coto],
    )?;
    assert_geolocated_in_scope(
        &mut ds,
        Scope::Node(node.uuid),
        vec![&root_coto, &child1_coto, &child2_coto, &child3_coto],
    )?;
    assert_geolocated_in_scope(
        &mut ds,
        Scope::Cotonoma((child1.uuid, CotonomaScope::Local)),
        vec![&child1_coto],
    )?;
    assert_geolocated_in_scope(
        &mut ds,
        Scope::Cotonoma((child1.uuid, CotonomaScope::Recursive)),
        vec![&child1_coto, &child2_coto, &child3_coto],
    )?;
    assert_geolocated_in_scope(
        &mut ds,
        Scope::Cotonoma((child1.uuid, CotonomaScope::Depth(1))),
        vec![&child1_coto, &child2_coto],
    )?;

    Ok(())
}

fn assert_geolocated_in_scope(
    ds: &mut DatabaseSession<'_>,
    scope: Scope,
    expect: Vec<&Coto>,
) -> Result<()> {
    let rows = ds.geolocated_cotos(scope, 100)?;
    let actual_ids: Vec<_> = rows.iter().map(|coto| coto.uuid).collect();
    assert_that!(actual_ids.len(), eq(expect.len()));
    for expected in expect {
        assert_that!(actual_ids.contains(&expected.uuid), eq(true));
    }
    Ok(())
}
