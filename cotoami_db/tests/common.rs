use anyhow::Result;
use chrono::{DateTime, Duration, Local};
use tempfile::{NamedTempFile, TempPath};

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
