use anyhow::Result;
use cotoami_db::{prelude::*, time};
use tempfile::{tempdir, NamedTempFile, TempDir, TempPath};

pub fn setup_db<'a>(name: &str) -> Result<(TempDir, Database, Node)> {
    setup_db_with_password(name, None)
}

pub fn setup_db_with_password<'a>(
    name: &str,
    password: Option<&str>,
) -> Result<(TempDir, Database, Node)> {
    time::clear_mock_time();
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let session = db.new_session()?;
    let ((_, node), _) = session.init_as_node(Some(name), password)?;
    drop(session);
    Ok((root_dir, db, node))
}

pub fn temp_file_path() -> Result<TempPath> {
    // NamedTempFile relies on Rust destructors to remove the temporary file
    let file = NamedTempFile::new()?;
    Ok(file.into_temp_path())
}

pub fn connect_parent_child(
    parent: &Database,
    child: &Database,
    url_prefix: &str,
    password: &str,
    child_node_input: ChildNodeInput,
) -> Result<()> {
    let mut parent_ds = parent.new_session()?;
    let parent_opr = parent.globals().local_node_as_operator()?;
    let parent_node = parent_ds.local_node()?;

    let child_ds = child.new_session()?;
    let child_opr = child.globals().local_node_as_operator()?;
    let child_node_id = child.globals().try_get_local_node_id()?;

    // Parent -> Child
    parent_ds.register_client_node(
        &child_node_id,
        password,
        NewDatabaseRole::Child(child_node_input),
        &parent_opr,
    )?;

    // Child -> Parent
    child_ds.import_node(&parent_node)?;
    child_ds.register_server_node_as_parent(&parent_node.uuid, url_prefix, &child_opr)?;

    let (changes, _) = parent_ds.chunk_of_changes(1, 100)?;
    for change in changes {
        child_ds.import_change(&change, &parent_node.uuid)?;
    }

    Ok(())
}
