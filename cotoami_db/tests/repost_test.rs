use anyhow::Result;
use cotoami_db::prelude::*;
use googletest::prelude::*;

pub mod common;

#[test]
fn repost() -> Result<()> {
    // Setup
    let (_root_dir, db, node) = common::setup_db("My Node")?;
    let mut ds = db.new_session()?;
    let opr = db.globals().local_node_as_operator()?;
    let (root, _) = ds.local_node_root()?.unwrap();

    let (coto, _) = ds.post_coto(&CotoInput::new("Cargo"), &root, &opr)?;
    let ((cotonoma1, _), _) = ds.post_cotonoma(&CotonomaInput::new("Rust"), &root, &opr)?;
    let ((cotonoma2, _), _) =
        ds.post_cotonoma(&CotonomaInput::new("Package manager"), &root, &opr)?;

    // When: repost a coto
    let ((repost1, original), changelog) = ds.repost(&coto.uuid, &cotonoma1, &opr)?;

    assert_that!(
        repost1,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&cotonoma1.uuid)),
            posted_by_id: eq(&node.uuid),
            content: none(),
            summary: none(),
            is_cotonoma: eq(&false),
            repost_of_id: some(eq(&coto.uuid)),
            reposted_in_ids: none()
        })
    );

    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            origin_node_id: eq(&node.uuid),
            change: pat!(Change::CreateCoto(eq(&Coto {
                rowid: 0,
                ..repost1
            })))
        })
    );

    assert_that!(
        original,
        pat!(Coto {
            uuid: eq(&coto.uuid),
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&root.uuid)),
            posted_by_id: eq(&node.uuid),
            content: some(eq("Cargo")),
            summary: none(),
            is_cotonoma: eq(&false),
            repost_of_id: none(),
            reposted_in_ids: some(pat!(Ids(elements_are![eq(&cotonoma1.uuid)]))),
            updated_at: eq(&repost1.created_at)
        })
    );

    // When: repost a repost
    let ((repost2, original), changelog) = ds.repost(&repost1.uuid, &cotonoma2, &opr)?;

    assert_that!(
        repost2,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&cotonoma2.uuid)),
            posted_by_id: eq(&node.uuid),
            content: none(),
            summary: none(),
            is_cotonoma: eq(&false),
            // The original should be the `coto`, not the `repost1`.
            repost_of_id: some(eq(&coto.uuid)),
            reposted_in_ids: none()
        })
    );

    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            origin_node_id: eq(&node.uuid),
            change: pat!(Change::CreateCoto(eq(&Coto {
                rowid: 0,
                ..repost2
            })))
        })
    );

    assert_that!(
        original,
        pat!(Coto {
            uuid: eq(&coto.uuid),
            content: some(eq("Cargo")),
            repost_of_id: none(),
            reposted_in_ids: some(pat!(Ids(elements_are![
                eq(&cotonoma1.uuid),
                eq(&cotonoma2.uuid)
            ]))),
            updated_at: eq(&repost2.created_at)
        })
    );

    // When: delete one of the two reposts
    let ChangelogEntry {
        change: Change::DeleteCoto { deleted_at, .. },
        ..
    } = ds.delete_coto(&repost2.uuid, &opr)?
    else {
        panic!("Unexpected changelog returned from delete_coto.");
    };

    // Then: expect the `reposted_in_ids` to be updated
    assert_eq!(ds.coto(&repost2.uuid)?, None);
    assert_that!(
        ds.coto(&coto.uuid)?,
        some(pat!(Coto {
            content: some(eq("Cargo")),
            repost_of_id: none(),
            reposted_in_ids: some(pat!(Ids(elements_are![eq(&cotonoma1.uuid)]))),
            updated_at: eq(&deleted_at)
        }))
    );

    // When: delete the original coto
    let _ = ds.delete_coto(&coto.uuid, &opr)?;

    // Then: expect cascading delete
    assert_eq!(ds.coto(&coto.uuid)?, None);
    assert_eq!(ds.coto(&repost1.uuid)?, None);

    // When: delete the only repost
    let (coto, _) = ds.post_coto(&CotoInput::new("Hello"), &root, &opr)?;
    let ((repost, _), _) = ds.repost(&coto.uuid, &cotonoma1, &opr)?;
    let _ = ds.delete_coto(&repost.uuid, &opr)?;

    // Then: expect `reposted_in_ids` should be updated to be null
    assert_eq!(ds.coto(&repost.uuid)?, None);
    assert_that!(
        ds.coto(&coto.uuid)?,
        some(pat!(Coto {
            content: some(eq("Hello")),
            reposted_in_ids: none(),
        }))
    );

    Ok(())
}
