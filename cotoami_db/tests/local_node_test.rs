use anyhow::Result;
use chrono::{offset::Utc, Duration};
use cotoami_db::{prelude::*, time};
use googletest::prelude::*;
use identicon_rs::Identicon;
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
    let mock_time = time::mock_time();
    let ((local_node, node), changelog) = ds.init_as_node(None, None)?;

    // then
    assert_that!(
        node,
        pat!(Node {
            rowid: eq(&1),
            name: eq(""),
            root_cotonoma_id: none(),
            version: eq(&1),
            created_at: eq(&mock_time),
        })
    );
    assert_icon_generated(&node)?;

    assert_that!(
        local_node,
        pat!(LocalNode {
            node_id: eq(&node.uuid),
            rowid: eq(&1),
            owner_password_hash: none(),
            owner_session_token: none(),
            owner_session_expires_at: none()
        })
    );

    let operator = db.globals().local_node_as_operator()?;
    assert_that!(
        ds.local_node_pair(&operator)?,
        eq(&(local_node, node.clone()))
    );
    assert_eq!(ds.node(&node.uuid)?.unwrap(), node);
    assert_that!(ds.all_nodes()?, elements_are![eq(&node)]);

    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&1),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&1),
            change: pat!(Change::CreateNode {
                node: eq(&Node { rowid: 0, ..node }),
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
    assert_that!(
        result,
        err(pat!(anyhow::Error{
            to_string(): eq("UNIQUE constraint failed: local_node.rowid")
        }))
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

    let ((local_node, _), _) = ds.init_as_node(None, Some("foo"))?;
    let mut owner = local_node.as_principal();

    // when: start_session with an invalid password
    assert_that!(owner.start_session("bar", duration), err(anything()));

    // when
    let mock_time = time::mock_time();
    let session_id = owner.start_session("foo", duration)?.to_owned();

    // then
    assert_that!(owner.session_token(), some(eq(&session_id)));
    assert_that!(
        owner.session_expires_at(),
        some(eq(&(mock_time + duration)))
    );
    owner.verify_session(&session_id)?;
    assert_that!(
        owner.verify_session("invalid-token"),
        err(displays_as(eq("The passed session token is invalid.")))
    );

    // when
    owner.set_session_expires_at(Some(Utc::now().naive_utc() - Duration::seconds(1)));

    // then
    assert_that!(
        owner.verify_session(&session_id),
        err(displays_as(eq("Session has been expired.")))
    );

    // when
    owner.clear_session();

    // then
    assert_that!(
        owner.verify_session(&session_id),
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
    let mock_time = time::mock_time();
    let ((local_node, node), changelog) = ds.init_as_node(Some("My Node"), None)?;

    // then
    let (root_cotonoma, root_coto) = ds.local_node_root()?.unwrap();

    assert_that!(
        node,
        pat!(Node {
            rowid: eq(&1),
            name: eq("My Node"),
            version: eq(&2), // root_cotonoma_id has been updated,
            created_at: eq(&mock_time),
        })
    );
    assert_icon_generated(&node)?;

    assert_that!(
        local_node,
        pat!(LocalNode {
            node_id: eq(&node.uuid),
            rowid: eq(&1),
            owner_password_hash: none(),
            owner_session_token: none(),
            owner_session_expires_at: none()
        })
    );

    let operator = db.globals().local_node_as_operator()?;
    assert_that!(
        ds.local_node_pair(&operator)?,
        eq(&(local_node, node.clone()))
    );
    assert_that!(ds.node(&node.uuid), ok(some(eq(&node))));
    assert_that!(ds.all_nodes()?, elements_are![eq(&node)]);

    assert_that!(
        root_cotonoma,
        pat!(Cotonoma {
            uuid: eq(&node.root_cotonoma_id.unwrap()),
            node_id: eq(&node.uuid),
            coto_id: eq(&root_coto.uuid),
            name: eq("My Node"),
            created_at: eq(&mock_time),
            updated_at: eq(&mock_time),
        })
    );
    assert_that!(ds.all_cotonomas(), ok(elements_are![eq(&root_cotonoma)]));

    assert_that!(
        root_coto,
        pat!(Coto {
            node_id: eq(&node.uuid),
            posted_in_id: none(),
            posted_by_id: eq(&node.uuid),
            content: none(),
            summary: some(eq("My Node")),
            is_cotonoma: eq(&true),
            repost_of_id: none(),
            reposted_in_ids: none(),
            created_at: eq(&mock_time),
            updated_at: eq(&mock_time),
        })
    );
    assert_that!(ds.all_cotos(), ok(elements_are![eq(&root_coto)]));

    assert_that!(
        changelog,
        pat!(ChangelogEntry {
            serial_number: eq(&1),
            origin_node_id: eq(&node.uuid),
            origin_serial_number: eq(&1),
            change: pat!(Change::CreateNode {
                node: eq(&Node { rowid: 0, ..node }),
                root: some(eq(&(
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

#[test]
fn set_icon() -> Result<()> {
    // setup
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let ds = db.new_session()?;
    let ((_, node), _) = ds.init_as_node(Some("My Node"), None)?;
    let operator = db.globals().local_node_as_operator()?;

    // when
    let new_icon = Identicon::new("test")
        .set_scale(1000)?
        .set_border(100)
        .export_jpeg_data()?;
    let (new_node, _) = ds.set_local_node_icon(new_icon.as_ref(), &operator)?;

    // then
    assert_that!(new_node.icon, not(eq(&node.icon)));

    assert_that!(
        image::guess_format(new_node.icon.as_ref()),
        ok(eq(&ImageFormat::Png))
    );

    let saved_image = image::load_from_memory(new_node.icon.as_ref())?;
    assert_that!(saved_image.width(), eq(Node::ICON_MAX_SIZE));
    assert_that!(saved_image.height(), eq(Node::ICON_MAX_SIZE));

    Ok(())
}

fn assert_icon_generated(node: &Node) -> Result<()> {
    let image = image::load_from_memory_with_format(node.icon.as_ref(), ImageFormat::Png)?;
    assert_that!(image.width(), eq(Node::ICON_MAX_SIZE));
    assert_that!(image.height(), eq(Node::ICON_MAX_SIZE));
    Ok(())
}
