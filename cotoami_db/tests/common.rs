use chrono::{DateTime, Duration, Local};

pub fn assert_approximately_now(datetime: &DateTime<Local>) {
    let now = chrono::offset::Local::now();
    assert!(
        now - *datetime < Duration::seconds(10),
        "{:?} should be approximately the same as the current timestamp {:?}",
        datetime,
        now
    )
}
