use anyhow::Result;
use cotoami_db::Database;
use tempfile::tempdir;

#[test]
fn default_state() -> Result<()> {
    let root_dir = tempdir()?;
    let db = Database::new(&root_dir)?;
    let mut session = db.create_session()?;

    assert_eq!(session.as_node()?, None);

    Ok(())
}
