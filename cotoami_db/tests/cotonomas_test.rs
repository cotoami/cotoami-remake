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
    let operator = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.local_node_root()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // When: post_cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let mock_time = time::mock_time();
    let ((cotonoma, coto), changelog) =
        ds.post_cotonoma(&CotonomaInput::new("test"), &root_cotonoma, &operator)?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            node_id: eq(&node.uuid),
            name: eq("test"),
            coto_id: eq(&coto.uuid),
            created_at: eq(&mock_time),
            updated_at: eq(&mock_time),
        })
    );
    assert_eq!(cotonoma.created_at, cotonoma.updated_at);

    assert_that!(
        coto,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&root_cotonoma.uuid)),
            posted_by_id: eq(&node.uuid),
            content: none(),
            summary: some(eq("test")),
            is_cotonoma: eq(&true),
        })
    );
    assert_eq!(coto.created_at, coto.updated_at);
    assert_eq!(cotonoma.created_at, coto.created_at);

    let root_cotonoma = ds.try_get_cotonoma(&root_cotonoma.uuid)?;
    assert_eq!(root_cotonoma.updated_at, coto.created_at);

    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            origin_node_id: eq(&node.uuid),
            change: pat!(Change::CreateCotonoma(
                eq(&cotonoma),
                eq(&Coto { rowid: 0, ..coto })
            )),
        })
    );

    assert_that!(ds.count_posts(&cotonoma.uuid)?, eq(0));

    /////////////////////////////////////////////////////////////////////////////
    // When: post a coto in the cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let (coto2, _) = ds.post_coto(&CotoInput::new("hello"), &cotonoma.uuid, &operator)?;

    assert_that!(
        coto2,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&cotonoma.uuid)),
            posted_by_id: eq(&node.uuid),
            content: some(eq("hello")),
        })
    );

    let cotonoma = ds.try_get_cotonoma(&cotonoma.uuid)?;
    assert_eq!(cotonoma.updated_at, coto2.created_at);

    assert_that!(ds.count_posts(&cotonoma.uuid)?, eq(1));

    /////////////////////////////////////////////////////////////////////////////
    // When: rename the cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let mock_time = time::mock_time();
    let ((cotonoma, coto), changelog) = ds.rename_cotonoma(&cotonoma.uuid, "test2", &operator)?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            name: eq("test2"),
            updated_at: eq(&mock_time),
        })
    );
    assert_that!(
        coto,
        pat!(Coto {
            summary: some(eq("test2")),
            updated_at: eq(&mock_time),
        })
    );
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            origin_node_id: eq(&node.uuid),
            change: pat!(Change::RenameCotonoma {
                cotonoma_id: eq(&cotonoma.uuid),
                name: eq("test2"),
                updated_at: eq(&mock_time)
            }),
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete the cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let result = ds.delete_coto(&coto.uuid, &operator);

    assert_that!(
        result,
        err(pat!(anyhow::Error{
            to_string(): eq("FOREIGN KEY constraint failed")
        }))
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: delete a cotonoma after its posts deleted
    /////////////////////////////////////////////////////////////////////////////

    let _ = ds.delete_coto(&coto2.uuid, &operator)?;
    let changelog = ds.delete_coto(&coto.uuid, &operator)?;

    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            origin_node_id: eq(&node.uuid),
            change: pat!(Change::DeleteCoto {
                coto_id: eq(&coto.uuid),
            })
        })
    );
    assert_that!(ds.coto(&coto.uuid), ok(none()));
    assert_that!(ds.cotonoma(&cotonoma.uuid), ok(none()));

    /////////////////////////////////////////////////////////////////////////////
    // When: rename the root cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let mock_time = time::mock_time();
    let ((cotonoma, coto), changelog) =
        ds.rename_cotonoma(&root_cotonoma.uuid, "Our Node", &operator)?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            name: eq("Our Node"),
            updated_at: eq(&mock_time),
        })
    );
    assert_that!(
        coto,
        pat!(Coto {
            summary: some(eq("Our Node")),
            updated_at: eq(&mock_time),
        })
    );
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            origin_node_id: eq(&node.uuid),
            change: pat!(Change::RenameNode {
                node_id: eq(&node.uuid),
                name: eq("Our Node"),
                updated_at: eq(&mock_time)
            }),
        })
    );
    assert_that!(ds.local_node()?.name, eq("Our Node"));

    Ok(())
}
