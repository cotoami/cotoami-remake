// In SQLite, INTEGER PRIMARY KEY is treated differently and is always 64-bit
// according to https://sqlite.org/autoinc.html.
// https://github.com/diesel-rs/diesel/issues/852

diesel::table! {
    nodes (rowid) {
        rowid -> BigInt,
        uuid -> Text,
        name -> Text,
        icon -> Binary,
        root_cotonoma_id -> Nullable<Text>,
        owner_password_hash -> Nullable<Text>,
        version -> Integer,
        created_at -> Timestamp,
        inserted_at -> Timestamp,
    }
}

diesel::allow_tables_to_appear_in_same_query!(nodes,);
