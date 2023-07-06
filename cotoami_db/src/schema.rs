//! Diesel schema definition
//!
//! <https://diesel.rs/guides/schema-in-depth.html>

// In SQLite, INTEGER PRIMARY KEY is treated differently and is always 64-bit
// according to <https://sqlite.org/autoinc.html>.
// <https://github.com/diesel-rs/diesel/issues/852>

diesel::allow_tables_to_appear_in_same_query!(
    nodes,
    local_node,
    parent_nodes,
    child_nodes,
    incorporated_nodes,
    cotos,
    cotonomas,
    links,
    changelog
);

/////////////////////////////////////////////////////////////////////////////
// Node (related structs are in `models::node`)
/////////////////////////////////////////////////////////////////////////////

diesel::table! {
    nodes (uuid) {
        uuid -> Text,
        rowid -> BigInt,
        icon -> Binary,
        name -> Text,
        root_cotonoma_id -> Nullable<Text>,
        version -> Integer,
        created_at -> Timestamp,
    }
}

diesel::table! {
    local_node (node_id) {
        node_id -> Text,
        rowid -> BigInt,
        owner_password_hash -> Nullable<Text>,
        owner_session_key -> Nullable<Text>,
        owner_session_expires_at -> Nullable<Timestamp>,
    }
}
diesel::joinable!(local_node -> nodes (node_id));

diesel::table! {
    parent_nodes (node_id) {
        node_id -> Text,
        url_prefix -> Text,
        created_at -> Timestamp,
    }
}
diesel::joinable!(parent_nodes -> nodes (node_id));

diesel::table! {
    child_nodes (node_id) {
        node_id -> Text,
        password_hash -> Text,
        can_edit_links -> Bool,
        created_at -> Timestamp,
    }
}
diesel::joinable!(child_nodes -> nodes (node_id));

diesel::table! {
    incorporated_nodes (node_id) {
        node_id -> Text,
        created_at -> Timestamp,
    }
}
diesel::joinable!(incorporated_nodes -> nodes (node_id));

/////////////////////////////////////////////////////////////////////////////
// Coto graph (related structs are in `models::coto`)
/////////////////////////////////////////////////////////////////////////////

diesel::table! {
    cotos (uuid) {
        uuid -> Text,
        rowid -> BigInt,
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
diesel::joinable!(cotos -> nodes (node_id));

diesel::table! {
    cotonomas (uuid) {
        uuid -> Text,
        node_id -> Text,
        coto_id -> Text,
        name -> Text,
        created_at -> Timestamp,
        updated_at -> Timestamp,
    }
}
diesel::joinable!(cotonomas -> nodes (node_id));
diesel::joinable!(cotonomas -> cotos (coto_id));

diesel::table! {
    links (uuid) {
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
diesel::joinable!(links -> nodes (node_id));

/////////////////////////////////////////////////////////////////////////////
// Changelog (related structs are in `models::changelog`)
/////////////////////////////////////////////////////////////////////////////

diesel::table! {
    changelog (serial_number) {
        serial_number -> BigInt,
        parent_node_id -> Nullable<Text>,
        parent_serial_number -> Nullable<BigInt>,
        change -> Binary,
        inserted_at -> Timestamp,
    }
}
