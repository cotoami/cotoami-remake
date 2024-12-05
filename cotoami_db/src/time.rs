//! Mockable `current_datetime`.

use std::cell::RefCell;

use chrono::{offset::Utc, NaiveDateTime};

thread_local! {
    static MOCK_TIME: RefCell<Option<NaiveDateTime>> = RefCell::new(None);
}

pub fn current_datetime() -> NaiveDateTime {
    MOCK_TIME.with(|cell| {
        cell.borrow()
            .as_ref()
            .cloned()
            .unwrap_or_else(|| Utc::now().naive_utc())
    })
}

pub fn set_mock_time(time: NaiveDateTime) {
    MOCK_TIME.with(|cell| *cell.borrow_mut() = Some(time));
}

pub fn mock_time() -> NaiveDateTime {
    let time = Utc::now().naive_utc();
    set_mock_time(time);
    time
}

pub fn clear_mock_time() { MOCK_TIME.with(|cell| *cell.borrow_mut() = None); }
