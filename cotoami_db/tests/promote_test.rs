use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn promote() -> Result<()> {
    /////////////////////////////////////////////////////////////////////////////
    // Setup
    /////////////////////////////////////////////////////////////////////////////

    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root_cotonoma, _) = ds.local_node_root()?.unwrap();

    /////////////////////////////////////////////////////////////////////////////
    // When: only content
    /////////////////////////////////////////////////////////////////////////////

    let (coto, _) = ds.post_coto(&CotoInput::new("Hello, world!"), &root_cotonoma.uuid, &opr)?;
    let ((cotonoma, coto), changelog) = ds.promote(&coto.uuid, &opr)?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            node_id: eq(&node.uuid),
            name: eq("Hello, world!"),
            coto_id: eq(&coto.uuid),
            created_at: eq(&coto.updated_at),
            updated_at: eq(&coto.updated_at),
        })
    );
    assert_that!(
        coto,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&root_cotonoma.uuid)),
            posted_by_id: eq(&node.uuid),
            content: none(), // the content should be moved to the summary
            summary: some(eq("Hello, world!")),
            is_cotonoma: eq(&true),
        })
    );
    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            origin_node_id: eq(&node.uuid),
            change: pat!(Change::Promote {
                coto_id: eq(&coto.uuid),
                promoted_at: eq(&coto.updated_at)
            }),
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: try to promote a coto that is already a cotonoma
    /////////////////////////////////////////////////////////////////////////////

    let result = ds.promote(&coto.uuid, &opr);
    assert_that!(
        result,
        err(pat!(anyhow::Error{
            to_string(): eq("The coto is already a cotonoma.")
        }))
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: only long content
    /////////////////////////////////////////////////////////////////////////////

    let (coto, _) = ds.post_coto(
        // 51 chars content
        &CotoInput::new("012345678901234567890123456789012345678901234567890"),
        &root_cotonoma.uuid,
        &opr,
    )?;
    let ((cotonoma, coto), _) = ds.promote(&coto.uuid, &opr)?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            name: eq("01234567890123456789012345678901234567890123456789"),
            coto_id: eq(&coto.uuid),
        })
    );
    assert_that!(
        coto,
        pat!(Coto {
            content: some(eq("012345678901234567890123456789012345678901234567890")),
            summary: some(eq("01234567890123456789012345678901234567890123456789")),
            is_cotonoma: eq(&true),
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: with summary
    /////////////////////////////////////////////////////////////////////////////

    let (coto, _) = ds.post_coto(
        &CotoInput::new("Hello, world!").summary("hello"),
        &root_cotonoma.uuid,
        &opr,
    )?;
    let ((cotonoma, coto), _) = ds.promote(&coto.uuid, &opr)?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            name: eq("hello"),
            coto_id: eq(&coto.uuid),
        })
    );
    assert_that!(
        coto,
        pat!(Coto {
            content: some(eq("Hello, world!")),
            summary: some(eq("hello")),
            is_cotonoma: eq(&true),
        })
    );

    /////////////////////////////////////////////////////////////////////////////
    // When: with long summary
    /////////////////////////////////////////////////////////////////////////////

    let (coto, _) = ds.post_coto(
        &CotoInput::new("Hello, world!")
            // 51 chars summary
            .summary("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxy"),
        &root_cotonoma.uuid,
        &opr,
    )?;
    let ((cotonoma, coto), _) = ds.promote(&coto.uuid, &opr)?;

    assert_that!(
        cotonoma,
        pat!(Cotonoma {
            name: eq("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwx"),
            coto_id: eq(&coto.uuid),
        })
    );
    assert_that!(
        coto,
        pat!(Coto {
            content: some(eq("Hello, world!")),
            summary: some(eq("abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwx")),
            is_cotonoma: eq(&true),
        })
    );

    Ok(())
}
