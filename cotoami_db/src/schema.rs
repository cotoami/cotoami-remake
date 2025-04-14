//! Diesel schema definition
//!
//! <https://diesel.rs/guides/schema-in-depth.html>

// In SQLite, INTEGER PRIMARY KEY is treated differently and is always 64-bit
// according to <https://sqlite.org/autoinc.html>.
// <https://github.com/diesel-rs/diesel/issues/852>

diesel::allow_tables_to_appear_in_same_query!(
    nodes,
    local_node,
    server_nodes,
    client_nodes,
    parent_nodes,
    child_nodes,
    cotos,
    cotos_fts,
    cotos_fts_trigram,
    cotos_fts_trigram_vocab,
    cotonomas,
    itos,
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
        owner_session_token -> Nullable<Text>,
        owner_session_expires_at -> Nullable<Timestamp>,
        image_max_size -> Nullable<Integer>,
        anonymous_read_enabled -> Bool,
    }
}
diesel::joinable!(local_node -> nodes (node_id));

diesel::table! {
    server_nodes (node_id) {
        node_id -> Text,
        created_at -> Timestamp,
        url_prefix -> Text,
        encrypted_password -> Nullable<Binary>,
        disabled -> Bool,
    }
}
diesel::joinable!(server_nodes -> nodes (node_id));

diesel::table! {
    client_nodes (node_id) {
        node_id -> Text,
        created_at -> Timestamp,
        password_hash -> Text,
        session_token -> Nullable<Text>,
        session_expires_at -> Nullable<Timestamp>,
        disabled -> Bool,
        last_session_created_at -> Nullable<Timestamp>,
    }
}
diesel::joinable!(client_nodes -> nodes (node_id));

diesel::table! {
    parent_nodes (node_id) {
        node_id -> Text,
        created_at -> Timestamp,
        changes_received -> BigInt,
        last_change_received_at -> Nullable<Timestamp>,
        forked -> Bool,
    }
}
diesel::joinable!(parent_nodes -> nodes (node_id));

diesel::table! {
    child_nodes (node_id) {
        node_id -> Text,
        created_at -> Timestamp,
        as_owner -> Bool,
        can_edit_itos -> Bool,
        can_post_cotonomas -> Bool,
    }
}
diesel::joinable!(child_nodes -> nodes (node_id));

/////////////////////////////////////////////////////////////////////////////
// Coto (related structs are in `models::coto`)
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
        media_content -> Nullable<Binary>,
        media_type -> Nullable<Text>,
        is_cotonoma -> Bool,
        longitude -> Nullable<Double>,
        latitude -> Nullable<Double>,
        datetime_start ->  Nullable<Timestamp>,
        datetime_end -> Nullable<Timestamp>,
        repost_of_id -> Nullable<Text>,
        reposted_in_ids -> Nullable<Text>,
        created_at -> Timestamp,
        updated_at -> Timestamp,
    }
}
diesel::joinable!(cotos -> nodes (node_id));

diesel::table! {
    cotos_fts (uuid) {
        uuid -> Text,
        rowid -> BigInt,
        node_id -> Text,
        posted_in_id -> Nullable<Text>,
        posted_by_id -> Text,
        content -> Nullable<Text>,
        summary -> Nullable<Text>,
        media_content -> Nullable<Binary>,
        media_type -> Nullable<Text>,
        is_cotonoma -> Bool,
        longitude -> Nullable<Double>,
        latitude -> Nullable<Double>,
        datetime_start -> Nullable<Timestamp>,
        datetime_end -> Nullable<Timestamp>,
        repost_of_id -> Nullable<Text>,
        reposted_in_ids -> Nullable<Text>,
        created_at -> Timestamp,
        updated_at -> Timestamp,

        // A special column with the same name as the table,
        // which is matched against in a full-text query or used to specify a special INSERT command.
        // https://sqlite.org/fts5.html
        #[sql_name = "cotos_fts"]
        whole_row -> Text,

        // All FTS5 tables feature a special hidden column named "rank".
        // In a full-text query, column rank contains by default the same value as would be
        // returned by executing the bm25() auxiliary function with no trailing arguments.
        // The better the match, the numerically smaller the value returned.
        rank -> Float,
    }
}

diesel::table! {
    cotos_fts_trigram (uuid) {
        uuid -> Text,
        rowid -> BigInt,
        node_id -> Text,
        posted_in_id -> Nullable<Text>,
        posted_by_id -> Text,
        content -> Nullable<Text>,
        summary -> Nullable<Text>,
        media_content -> Nullable<Binary>,
        media_type -> Nullable<Text>,
        is_cotonoma -> Bool,
        longitude -> Nullable<Double>,
        latitude -> Nullable<Double>,
        datetime_start -> Nullable<Timestamp>,
        datetime_end -> Nullable<Timestamp>,
        repost_of_id -> Nullable<Text>,
        reposted_in_ids -> Nullable<Text>,
        created_at -> Timestamp,
        updated_at -> Timestamp,

        #[sql_name = "cotos_fts_trigram"]
        whole_row -> Text,
        rank -> Float,
    }
}

diesel::table! {
    // This table contains one row for each distinct term
    // in the associated FTS5 table `cotos_fts_trigram`.
    cotos_fts_trigram_vocab (term) {
        term -> Text,
        doc -> BigInt,
        cnt -> BigInt,
    }
}

/////////////////////////////////////////////////////////////////////////////
// Cotonoma (related structs are in `models::cotonoma`)
/////////////////////////////////////////////////////////////////////////////

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

/////////////////////////////////////////////////////////////////////////////
// Ito (related structs are in `models::ito`)
/////////////////////////////////////////////////////////////////////////////

diesel::table! {
    itos (uuid) {
        uuid -> Text,
        node_id -> Text,
        created_by_id -> Text,
        source_coto_id -> Text,
        target_coto_id -> Text,
        description -> Nullable<Text>,
        details -> Nullable<Text>,
        order -> Integer,
        created_at -> Timestamp,
        updated_at -> Timestamp,
    }
}
diesel::joinable!(itos -> nodes (node_id));

/////////////////////////////////////////////////////////////////////////////
// Changelog (related structs are in `models::changelog`)
/////////////////////////////////////////////////////////////////////////////

diesel::table! {
    changelog (serial_number) {
        serial_number -> BigInt,
        origin_node_id -> Text,
        origin_serial_number -> BigInt,
        change -> Binary,
        import_error -> Nullable<Text>,
        inserted_at -> Timestamp,
    }
}
