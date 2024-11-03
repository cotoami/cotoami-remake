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
    let ((cotonoma, _), _) = ds.post_cotonoma(&CotonomaInput::new("Rust"), &root, &opr)?;

    // When
    let (repost, changelog) = ds.repost(&coto.uuid, &cotonoma, &opr)?;

    assert_that!(
        repost,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&cotonoma.uuid)),
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
            change: pat!(Change::Repost {
                coto_id: eq(&coto.uuid),
                dest: eq(&cotonoma.uuid),
                reposted_by: eq(&node.uuid),
                reposted_at: eq(&repost.created_at)
            })
        })
    );

    assert_that!(
        ds.coto(&coto.uuid)?,
        some(pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: some(eq(&root.uuid)),
            posted_by_id: eq(&node.uuid),
            content: some(eq("Cargo")),
            summary: none(),
            is_cotonoma: eq(&false),
            repost_of_id: none(),
            reposted_in_ids: some(pat!(Ids(elements_are![eq(&cotonoma.uuid)]))),
            updated_at: eq(&repost.created_at)
        }))
    );

    Ok(())
}
