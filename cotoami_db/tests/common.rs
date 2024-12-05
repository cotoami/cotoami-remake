use anyhow::Result;
use cotoami_db::{prelude::*, time};
use tempfile::{tempdir, NamedTempFile, TempDir, TempPath};

pub fn setup_db<'a>(name: &str) -> Result<(TempDir, Database, Node)> {
    time::clear_mock_time();
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let session = db.new_session()?;
    let ((_, node), _) = session.init_as_node(Some(name), None)?;
    drop(session);
    Ok((root_dir, db, node))
}

pub fn temp_file_path() -> Result<TempPath> {
    // NamedTempFile relies on Rust destructors to remove the temporary file
    let file = NamedTempFile::new()?;
    Ok(file.into_temp_path())
}
