//! Diesel schema definition
//!
//! <https://diesel.rs/guides/schema-in-depth.html>

// In SQLite, INTEGER PRIMARY KEY is treated differently and is always 64-bit
// according to <https://sqlite.org/autoinc.html>.
// <https://github.com/diesel-rs/diesel/issues/852>

diesel::allow_tables_to_appear_in_same_query!(
    nodes,
    parent_nodes,
    child_nodes,
    imported_nodes,
    cotos,
    cotonomas,
    links
);

/////////////////////////////////////////////////////////////////////////////
// Node (related structs are in `models::node`)
/////////////////////////////////////////////////////////////////////////////

diesel::table! {
    nodes (rowid) {
        rowid -> BigInt,
        uuid -> Text,
        icon -> Binary,
        name -> Text,
        root_cotonoma_id -> Nullable<Text>,
        owner_password_hash -> Nullable<Text>,
        version -> Integer,
        created_at -> Timestamp,
        inserted_at -> Timestamp,
    }
}

diesel::table! {
    parent_nodes (rowid) {
        rowid -> BigInt,
        node_id -> Text,
        url_prefix -> Text,
        created_at -> Timestamp,
    }
}

diesel::table! {
    child_nodes (rowid) {
        rowid -> BigInt,
        node_id -> Text,
        password_hash -> Text,
        can_edit_links -> Bool,
        created_at -> Timestamp,
    }
}

diesel::table! {
    imported_nodes (rowid) {
        rowid -> BigInt,
        node_id -> Text,
        created_at -> Timestamp,
    }
}

/////////////////////////////////////////////////////////////////////////////
// Coto graph (related structs are in `models::coto`)
/////////////////////////////////////////////////////////////////////////////

diesel::table! {
    cotos (rowid) {
        rowid -> BigInt,
        uuid -> Text,
        node_id -> Text,
        posted_in_id -> Nullable<Text>,
        posted_by_id -> Text,
        content -> Nullable<Text>,
        summary -> Nullable<Text>,
        is_cotonoma -> Bool,
        repost_of_id -> Nullable<Text>,
        reposted_in_ids -> Nullable<Text>,
        created_at -> Timestamp,
        updated_at -> Timestamp,
    }
}

diesel::table! {
    cotonomas (rowid) {
        rowid -> BigInt,
        uuid -> Text,
        node_id -> Text,
        coto_id -> Text,
        name -> Text,
        created_at -> Timestamp,
        updated_at -> Timestamp,
    }
}

diesel::table! {
    links (rowid) {
        rowid -> BigInt,
        uuid -> Text,
        node_id -> Text,
        created_by_id -> Text,
        tail_coto_id -> Text,
        head_coto_id -> Text,
        linking_phrase -> Nullable<Text>,
        created_at -> Timestamp,
        updated_at -> Timestamp,
    }
}
