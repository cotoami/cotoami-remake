// In SQLite, INTEGER PRIMARY KEY is treated differently and is always 64-bit
// according to https://sqlite.org/autoinc.html.
// https://github.com/diesel-rs/diesel/issues/852

diesel::table! {
    nodes (rowid) {
        rowid -> BigInt,
        uuid -> Text,
        name -> Nullable<Text>,
        icon -> Nullable<Binary>,
        url_prefix -> Nullable<Text>,
        password -> Nullable<Text>,
        can_edit_links -> Integer,
        version -> Integer,
        created_at -> Timestamp,
    }
}
