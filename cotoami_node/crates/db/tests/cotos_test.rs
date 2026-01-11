use anyhow::Result;
use cotoami_db::{prelude::*, time};
use googletest::prelude::*;

pub mod common;

#[test]
fn crud_operations() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.local_node_root()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // When: post_coto
    /////////////////////////////////////////////////////////////////////////////

    let mock_time = time::mock_time();
    let (coto, changelog) = ds.post_coto(&CotoInput::new("hello"), &root_cotonoma.uuid, &opr)?;

    // check the inserted coto
    assert_that!(
        coto,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&root_cotonoma.uuid)),
            posted_by_id: eq(&node.uuid),
            content: some(eq("hello")),
            summary: none(),
            is_cotonoma: eq(&false),
            repost_of_id: none(),
            reposted_in_ids: none(),
            created_at: eq(&mock_time),
            updated_at: eq(&mock_time),
            ..
        })
    );

    // check if it is stored in the db
    assert!(ds.contains_coto(&coto.uuid)?);
    assert_that!(ds.coto(&coto.uuid)?, some(eq(&coto)));

    // check if `recent_cotos` contains it
    assert_that!(
        ds.recent_cotos(Scope::cotonoma_local(root_cotonoma.uuid), false, 5, 0)?,
        pat!(Page {
            size: eq(&5),
            index: eq(&0),
            total_rows: eq(&1),
            rows: elements_are![eq(&coto)]
        })
    );

    // check if the timestamp of the cotonoma has been updated
    let cotonoma = ds.try_get_cotonoma(&root_cotonoma.uuid)?;
    assert_that!(cotonoma.updated_at, eq(coto.created_at));

    // check the super cotonomas
    assert_that!(ds.super_cotonomas(&coto)?, elements_are![eq(&cotonoma)]);

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&2),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&2),
            change: pat!(Change::CreateCoto(eq(&Coto { rowid: 0, ..coto }))),
            ..
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: edit_coto (1: change content, add summary)
    /////////////////////////////////////////////////////////////////////////////

    let mock_time = time::mock_time();
    let diff = CotoContentDiff::default()
        .content("bar")
        .summary(Some("foo"));
    let (edited_coto, changelog) = ds.edit_coto(&coto.uuid, diff, &opr)?;

    // check the edited coto
    assert_that!(
        edited_coto,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&root_cotonoma.uuid)),
            posted_by_id: eq(&node.uuid),
            content: some(eq("bar")),
            summary: some(eq("foo")),
            is_cotonoma: eq(&false),
            repost_of_id: none(),
            reposted_in_ids: none(),
            updated_at: eq(&mock_time),
            ..
        })
    );

    // check if it is stored in the db
    assert_that!(ds.coto(&coto.uuid)?, some(eq(&edited_coto)));

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&3),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&3),
            change: pat!(Change::EditCoto {
                coto_id: eq(&coto.uuid),
                diff: pat!(CotoContentDiff {
                    content: pat!(FieldDiff::Change(eq("bar"))),
                    summary: pat!(FieldDiff::Change(eq("foo"))),
                    media_content: eq(&FieldDiff::None),
                    geolocation: eq(&FieldDiff::None),
                    ..
                }),
                updated_at: eq(&edited_coto.updated_at),
            }),
            ..
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: edit_coto (2: remove summary)
    /////////////////////////////////////////////////////////////////////////////

    let diff = CotoContentDiff::default().summary(None);
    assert_that!(
        diff,
        pat!(CotoContentDiff {
            content: eq(&FieldDiff::None),
            summary: eq(&FieldDiff::Delete),
            media_content: eq(&FieldDiff::None),
            geolocation: eq(&FieldDiff::None),
            ..
        })
    );
    let (edited_coto, changelog) = ds.edit_coto(&coto.uuid, diff, &opr)?;

    // check the edited coto
    assert_that!(
        edited_coto,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&root_cotonoma.uuid)),
            posted_by_id: eq(&node.uuid),
            content: some(eq("bar")),
            summary: none(),
            is_cotonoma: eq(&false),
            repost_of_id: none(),
            reposted_in_ids: none(),
            ..
        })
    );

    // check if it is stored in the db
    assert_that!(ds.coto(&coto.uuid)?, some(eq(&edited_coto)));

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&4),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&4),
            change: pat!(Change::EditCoto {
                coto_id: eq(&coto.uuid),
                diff: pat!(CotoContentDiff {
                    content: eq(&FieldDiff::None),
                    summary: eq(&FieldDiff::Delete),
                    media_content: eq(&FieldDiff::None),
                    geolocation: eq(&FieldDiff::None),
                    ..
                }),
                updated_at: eq(&edited_coto.updated_at),
            }),
            ..
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete_coto
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds.delete_coto(&coto.uuid, &opr)?;

    // check if it is deleted from the db
    assert!(!ds.contains_coto(&coto.uuid)?);
    assert_that!(ds.coto(&coto.uuid)?, none());
    assert_that!(
        ds.recent_cotos(Scope::cotonoma_local(root_cotonoma.uuid), false, 5, 0)?,
        pat!(Page {
            size: eq(&5),
            index: eq(&0),
            total_rows: eq(&0),
            ..
        })
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&5),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&5),
            change: pat!(Change::DeleteCoto {
                coto_id: eq(&coto.uuid),
                ..
            }),
            ..
        })
    );

    Ok(())
}
