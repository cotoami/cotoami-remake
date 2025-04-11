use anyhow::Result;
use cotoami_db::{prelude::*, time};
use googletest::prelude::*;

pub mod common;

#[test]
fn crud_operations() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup: create coto1, coto2, coto2
    /////////////////////////////////////////////////////////////////////////////

    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.local_node_root()?.unwrap();

    let (coto1, _) = ds.post_coto(&CotoInput::new("coto1"), &root_cotonoma.uuid, &opr)?;
    let (coto2, _) = ds.post_coto(&CotoInput::new("coto2"), &root_cotonoma.uuid, &opr)?;
    let (coto3, _) = ds.post_coto(&CotoInput::new("coto3"), &root_cotonoma.uuid, &opr)?;
    let (coto4, _) = ds.post_coto(&CotoInput::new("coto4"), &root_cotonoma.uuid, &opr)?;

    /////////////////////////////////////////////////////////////////////////////
    // When: create ito1: coto1 => coto2
    /////////////////////////////////////////////////////////////////////////////

    let mock_time = time::mock_time();
    let (ito1, changelog) = ds.create_ito(
        &ItoInput::new(coto1.uuid, coto2.uuid).description("hello"),
        &opr,
    )?;

    // check the created ito
    assert_that!(
        ito1,
        pat!(Ito {
            node_id: eq(&node.uuid),
            created_by_id: eq(&node.uuid),
            source_coto_id: eq(&coto1.uuid),
            target_coto_id: eq(&coto2.uuid),
            description: some(eq("hello")),
            details: none(),
            order: eq(&1),
            created_at: eq(&mock_time),
            updated_at: eq(&mock_time),
        })
    );

    // check if it is stored in the db
    assert_eq!(ds.ito(&ito1.uuid)?.as_ref(), Some(&ito1));

    // check if `recent_itos` contains it
    assert_that!(
        ds.recent_itos(None, 5, 0)?,
        pat!(Page {
            size: eq(&5),
            index: eq(&0),
            total_rows: eq(&1),
            rows: elements_are![eq(&ito1)]
        })
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&6),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&6),
            change: pat!(Change::CreateIto(eq(&ito1))),
            import_error: none()
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: create ito2: coto1 => coto3
    /////////////////////////////////////////////////////////////////////////////

    time::clear_mock_time();
    let (ito2, _) = ds.create_ito(
        &ItoInput::new(coto1.uuid, coto3.uuid)
            .description("bye")
            .details("some details"),
        &opr,
    )?;

    // check the created ito
    assert_that!(
        ito2,
        pat!(Ito {
            source_coto_id: eq(&coto1.uuid),
            target_coto_id: eq(&coto3.uuid),
            description: some(eq("bye")),
            details: some(eq("some details")),
            order: eq(&2)
        })
    );

    // check if it is stored in the db
    assert_that!(ds.ito(&ito2.uuid)?, some(eq(&ito2)));

    // check if `recent_itos` contains it
    assert_that!(
        ds.recent_itos(None, 5, 0)?,
        pat!(Page {
            size: eq(&5),
            index: eq(&0),
            total_rows: eq(&2),
            rows: elements_are![eq(&ito2), eq(&ito1)]
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: create ito3: coto1 =(order number 1)=> coto4
    /////////////////////////////////////////////////////////////////////////////

    let (ito3, _) = ds.create_ito(&ItoInput::new(coto1.uuid, coto4.uuid).order(1), &opr)?;

    // check if the order of the itos has been updated
    assert_eq!(ds.ito(&ito3.uuid)?.unwrap().order, 1);
    assert_eq!(ds.ito(&ito1.uuid)?.unwrap().order, 2);
    assert_eq!(ds.ito(&ito2.uuid)?.unwrap().order, 3);

    /////////////////////////////////////////////////////////////////////////////
    // When: move ito2 to the head
    /////////////////////////////////////////////////////////////////////////////

    let (_, changelog) = ds.change_ito_order(&ito2.uuid, 1, &opr)?;

    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&9),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&9),
            change: pat!(Change::ChangeItoOrder {
                ito_id: eq(&ito2.uuid),
                new_order: eq(&1)
            }),
            import_error: none()
        })
    );
    assert_eq!(ds.ito(&ito2.uuid)?.unwrap().order, 1);
    assert_eq!(ds.ito(&ito3.uuid)?.unwrap().order, 2);
    assert_eq!(ds.ito(&ito1.uuid)?.unwrap().order, 3);

    /////////////////////////////////////////////////////////////////////////////
    // When: edit ito1
    /////////////////////////////////////////////////////////////////////////////

    let diff = ItoContentDiff::default()
        .description(Some("hello"))
        .details(Some("hello details"));
    let (edited_ito1, changelog) = ds.edit_ito(&ito1.uuid, diff, &opr)?;

    // check the edited ito
    assert_that!(
        edited_ito1,
        pat!(Ito {
            description: some(eq("hello")),
            details: some(eq("hello details"))
        })
    );

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&10),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&10),
            change: pat!(Change::EditIto {
                ito_id: eq(&ito1.uuid),
                diff: pat!(ItoContentDiff {
                    description: pat!(FieldDiff::Change(eq("hello"))),
                    details: pat!(FieldDiff::Change(eq("hello details"))),
                }),
                updated_at: eq(&edited_ito1.updated_at),
            }),
            import_error: none()
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete ito1
    /////////////////////////////////////////////////////////////////////////////

    let changelog = ds.delete_ito(&ito1.uuid, &opr)?;

    // check if it is deleted from the db
    assert_eq!(ds.ito(&ito1.uuid)?, None);

    // check the content of the ChangelogEntry
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&11),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&11),
            change: pat!(Change::DeleteIto {
                ito_id: eq(&ito1.uuid)
            }),
            import_error: none()
        })
    );

    Ok(())
}
