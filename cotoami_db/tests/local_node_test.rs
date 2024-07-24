use anyhow::Result;
use chrono::{offset::Utc, Duration};
use common::assert_approximately_now;
use cotoami_db::prelude::*;
use googletest::prelude::*;
use image::ImageFormat;
use tempfile::tempdir;

pub mod common;

#[test]
fn default_state() -> Result<()> {
    // when
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.new_session()?;

    // then
    assert_that!(db.globals().has_local_node(), eq(false));
    assert_that!(session.all_nodes()?, empty());

    Ok(())
}

#[test]
fn init_as_empty_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut ds = db.new_session()?;

    // when
    let ((local_node, node), changelog) = ds.init_as_node(None, None)?;

    // then
    assert_that!(
        node,
        matches_pattern!(Node {
            rowid: eq(1),
            name: eq(""),
            root_cotonoma_id: none(),
            version: eq(1)
        })
    );
    assert_icon_generated(&node)?;
    assert_approximately_now(node.created_at());

    assert_that!(
        local_node,
        matches_pattern!(LocalNode {
            node_id: eq(node.uuid),
            rowid: eq(1),
            owner_password_hash: none(),
            owner_session_token: none(),
            owner_session_expires_at: none()
        })
    );

    let operator = db.globals().local_node_as_operator()?;
    assert_that!(
        ds.local_node_pair(&operator)?,
        eq((local_node, node.clone()))
    );
    assert_eq!(ds.node(&node.uuid)?.unwrap(), node);
    assert_that!(ds.all_nodes()?, elements_are![eq_deref_of(&node)]);

    assert_that!(
        changelog,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(1),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(1),
            change: matches_pattern!(Change::CreateNode {
                node: eq(Node { rowid: 0, ..node }),
                root: none()
            }),
        })
    );

    Ok(())
}

#[test]
fn duplicate_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let ds = db.new_session()?;
    let _ = ds.init_as_node(None, None)?;

    // when
    let result = ds.init_as_node(None, None);

    // then
    assert_eq!(
        result.unwrap_err().to_string(),
        "UNIQUE constraint failed: local_node.rowid"
    );
    Ok(())
}

#[test]
fn owner_session() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let ds = db.new_session()?;
    let duration = Duration::minutes(30);

    // when
    let ((mut local_node, _), _) = ds.init_as_node(None, Some("foo"))?;

    // then
    assert_that!(local_node.start_session("bar", duration), err(anything()));

    let session_id = local_node.start_session("foo", duration)?.to_owned();
    assert_eq!(
        local_node.owner_session_token.as_deref().unwrap(),
        &session_id
    );
    assert_approximately_now(local_node.session_expires_at_as_local_time().unwrap() - duration);
    local_node.verify_session(&session_id)?;
    assert_that!(
        local_node.verify_session("invalid-token"),
        err(displays_as(eq("The passed session token is invalid.")))
    );

    // when
    local_node.owner_session_expires_at = Some(Utc::now().naive_utc() - Duration::seconds(1));

    // then
    assert_that!(
        local_node.verify_session(&session_id),
        err(displays_as(eq("Session has been expired.")))
    );

    // when
    local_node.clear_session();

    // then
    assert_that!(
        local_node.verify_session(&session_id),
        err(displays_as(eq("Session doesn't exist.")))
    );

    Ok(())
}

#[test]
fn init_as_node() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut ds = db.new_session()?;

    // when
    let ((local_node, node), changelog) = ds.init_as_node(Some("My Node"), None)?;

    // then
    let (root_cotonoma, root_coto) = ds.root_cotonoma()?.unwrap();

    assert_that!(
        node,
        matches_pattern!(Node {
            rowid: eq(1),
            name: eq("My Node"),
            version: eq(2), // root_cotonoma_id has been updated
        })
    );
    assert_icon_generated(&node)?;
    assert_approximately_now(node.created_at());

    assert_that!(
        local_node,
        matches_pattern!(LocalNode {
            node_id: eq(node.uuid),
            rowid: eq(1),
            owner_password_hash: none(),
            owner_session_token: none(),
            owner_session_expires_at: none()
        })
    );

    let operator = db.globals().local_node_as_operator()?;
    assert_that!(
        ds.local_node_pair(&operator)?,
        eq((local_node, node.clone()))
    );
    assert_that!(ds.node(&node.uuid), ok(some(eq_deref_of(&node))));
    assert_that!(ds.all_nodes()?, elements_are![eq_deref_of(&node)]);

    assert_that!(
        root_cotonoma,
        matches_pattern!(Cotonoma {
            uuid: eq(node.root_cotonoma_id.unwrap()),
            node_id: eq(node.uuid),
            coto_id: eq(root_coto.uuid),
            name: eq("My Node")
        })
    );
    assert_approximately_now(root_cotonoma.created_at());
    assert_approximately_now(root_cotonoma.updated_at());
    assert_that!(
        ds.all_cotonomas(),
        ok(elements_are![eq_deref_of(&root_cotonoma)])
    );

    assert_that!(
        root_coto,
        matches_pattern!(Coto {
            node_id: eq(node.uuid),
            posted_in_id: none(),
            posted_by_id: eq(node.uuid),
            content: none(),
            summary: some(eq("My Node")),
            is_cotonoma: eq(true),
            repost_of_id: none(),
            reposted_in_ids: none()
        })
    );
    assert_approximately_now(root_coto.created_at());
    assert_approximately_now(root_coto.updated_at());
    assert_that!(ds.all_cotos(), ok(elements_are![eq_deref_of(&root_coto)]));

    assert_that!(
        changelog,
        matches_pattern!(ChangelogEntry {
            serial_number: eq(1),
            origin_node_id: eq(node.uuid),
            origin_serial_number: eq(1),
            change: matches_pattern!(Change::CreateNode {
                node: eq(Node { rowid: 0, ..node }),
                root: some(eq((
                    root_cotonoma,
                    Coto {
                        rowid: 0,
                        ..root_coto
                    }
                )))
            })
        })
    );

    Ok(())
}

fn assert_icon_generated(node: &Node) -> Result<()> {
    let image = image::load_from_memory_with_format(node.icon.as_ref(), ImageFormat::Png)?;
    assert_that!(image.width(), eq(Node::ICON_MAX_SIZE));
    assert_that!(image.height(), eq(Node::ICON_MAX_SIZE));
    Ok(())
}
