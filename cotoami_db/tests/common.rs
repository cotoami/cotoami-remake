use anyhow::Result;
use chrono::{DateTime, Duration, Local};
use cotoami_db::{Database, Node};
use tempfile::{tempdir, NamedTempFile, TempDir, TempPath};

pub fn setup_db<'a>() -> Result<(TempDir, Database, Node)> {
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;
    let (node, _) = session.init_as_node("My Node", None)?;
    drop(session);
    Ok((root_dir, db, node))
}

pub fn assert_approximately_now(datetime: &DateTime<Local>) {
    let now = chrono::offset::Local::now();
    assert!(
        now - *datetime < Duration::seconds(10),
        "{:?} should be approximately the same as the current timestamp {:?}",
        datetime,
        now
    )
}

pub fn temp_file_path() -> Result<TempPath> {
    // NamedTempFile relies on Rust destructors to remove the temporary file
    let file = NamedTempFile::new()?;
    Ok(file.into_temp_path())
}
