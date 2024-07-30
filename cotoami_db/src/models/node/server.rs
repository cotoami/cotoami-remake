use aes_gcm::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    Aes256Gcm, Key,
};
use anyhow::Result;
use argon2::Argon2;
use chrono::NaiveDateTime;
use derive_new::new;
use diesel::{
    backend::Backend, deserialize::FromSql, expression::AsExpression, prelude::*, serialize::ToSql,
    sql_types::Binary, sqlite::Sqlite, FromSqlRow,
};
use generic_array::GenericArray;
use uuid::Uuid;
use validator::Validate;

use super::Node;
use crate::{models::Id, schema::server_nodes};

/////////////////////////////////////////////////////////////////////////////
// ServerNode
/////////////////////////////////////////////////////////////////////////////

/// A row in `server_nodes` table
#[derive(
    derive_more::Debug,
    Clone,
    PartialEq,
    Eq,
    Identifiable,
    Queryable,
    Selectable,
    serde::Serialize,
    serde::Deserialize,
)]
#[diesel(primary_key(node_id))]
pub struct ServerNode {
    /// UUID of this server node.
    pub node_id: Id<Node>,

    /// Date when this connection was created.
    pub created_at: NaiveDateTime,

    /// URL prefix to connect to this server node.
    pub url_prefix: String,

    /// Saved password to connect to this server node.
    #[debug(skip)]
    #[serde(skip_serializing, skip_deserializing)]
    pub encrypted_password: Option<EncryptedPassword>,

    /// Local node won't connect to this node if the value is TRUE.
    pub disabled: bool,
}

impl ServerNode {
    // 2000 characters minus 500 for an API path after the prefix.
    // cf. https://stackoverflow.com/a/417184
    pub const URL_PREFIX_MAX_LENGTH: usize = 1500;

    pub fn password(&self, encryption_password: &str) -> Result<Option<String>> {
        if let Some(ref encrypted_password) = self.encrypted_password {
            let plaintext = decrypt_password(encrypted_password, encryption_password)?;
            Ok(Some(plaintext))
        } else {
            Ok(None)
        }
    }

    pub(crate) fn to_update(&self) -> UpdateServerNode { UpdateServerNode::new(&self.node_id) }
}

/////////////////////////////////////////////////////////////////////////////
// EncryptedPassword
/////////////////////////////////////////////////////////////////////////////

#[derive(
    Debug, Clone, PartialEq, Eq, AsExpression, FromSqlRow, serde::Serialize, serde::Deserialize,
)]
#[diesel(sql_type = Binary)]
pub struct EncryptedPassword {
    salt: [u8; 16],
    nonce: Vec<u8>,
    ciphertext: Vec<u8>,
}

impl ToSql<Binary, Sqlite> for EncryptedPassword {
    fn to_sql<'b>(
        &'b self,
        out: &mut diesel::serialize::Output<'b, '_, Sqlite>,
    ) -> diesel::serialize::Result {
        let msgpack_bytes = rmp_serde::to_vec(&self)?;
        // https://diesel.rs/guides/migration_guide.html#changed-tosql-implementations
        out.set_value(msgpack_bytes);
        Ok(diesel::serialize::IsNull::No)
    }
}

impl FromSql<Binary, Sqlite> for EncryptedPassword {
    fn from_sql(value: <Sqlite as Backend>::RawValue<'_>) -> diesel::deserialize::Result<Self> {
        let msgpack_bytes = <Vec<u8> as FromSql<Binary, Sqlite>>::from_sql(value)?;
        Ok(rmp_serde::from_slice(&msgpack_bytes)?)
    }
}

/////////////////////////////////////////////////////////////////////////////
// NewServerNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` server node data
#[derive(Insertable, Validate)]
#[diesel(table_name = server_nodes)]
pub(crate) struct NewServerNode<'a> {
    node_id: &'a Id<Node>,
    created_at: NaiveDateTime,
    #[validate(url, length(max = "ServerNode::URL_PREFIX_MAX_LENGTH"))]
    url_prefix: &'a str,
}

impl<'a> NewServerNode<'a> {
    pub fn new(node_id: &'a Id<Node>, url_prefix: &'a str) -> Result<Self> {
        let server_node = Self {
            node_id,
            created_at: crate::current_datetime(),
            url_prefix,
        };
        server_node.validate()?;
        Ok(server_node)
    }
}

/////////////////////////////////////////////////////////////////////////////
// UpdateServerNode
/////////////////////////////////////////////////////////////////////////////

/// A changeset of [ServerNode] for update.
/// Only fields that have [Some] value will be updated.
#[derive(Debug, Identifiable, AsChangeset, Validate, new)]
#[diesel(table_name = server_nodes, primary_key(node_id))]
pub(crate) struct UpdateServerNode<'a> {
    node_id: &'a Id<Node>,

    #[new(default)]
    #[validate(url, length(max = "ServerNode::URL_PREFIX_MAX_LENGTH"))]
    pub url_prefix: Option<&'a str>,

    #[new(default)]
    pub encrypted_password: Option<Option<EncryptedPassword>>,

    #[new(default)]
    pub disabled: Option<bool>,
}

impl<'a> UpdateServerNode<'a> {
    pub fn set_password(&mut self, plaintext: &str, encryption_password: &str) -> Result<()> {
        let encrypted_password = encrypt_password(plaintext, encryption_password)?;
        self.encrypted_password = Some(Some(encrypted_password));
        Ok(())
    }
}

/////////////////////////////////////////////////////////////////////////////
// Internal functions
/////////////////////////////////////////////////////////////////////////////

fn encrypt_password(plaintext: &str, encryption_password: &str) -> Result<EncryptedPassword> {
    // Generate a salt.
    // A random salt is fine for that as long as its length is sufficient;
    // a 16-byte salt would work well (by definition, UUID are very good salts).
    // https://docs.rs/password-hash/0.5.0/password_hash/struct.Salt.html
    let salt = Uuid::new_v4().into_bytes();

    // Transform an `encryption_password` into an encryption key.
    let mut key = [0u8; 32];
    Argon2::default().hash_password_into(encryption_password.as_bytes(), &salt, &mut key)?;
    let key: &Key<Aes256Gcm> = &key.into();

    // Encrypt the password.
    let cipher = Aes256Gcm::new(key);
    let nonce = Aes256Gcm::generate_nonce(&mut OsRng);
    let ciphertext = cipher.encrypt(&nonce, plaintext.as_bytes())?;
    Ok(EncryptedPassword {
        salt,
        nonce: nonce.to_vec(),
        ciphertext,
    })
}

fn decrypt_password(
    encrypted_password: &EncryptedPassword,
    encryption_password: &str,
) -> Result<String> {
    // Transform an `encryption_password` into an encryption key.
    let mut key = [0u8; 32];
    Argon2::default().hash_password_into(
        encryption_password.as_bytes(),
        &encrypted_password.salt,
        &mut key,
    )?;
    let key: &Key<Aes256Gcm> = &key.into();

    // Decrypt the password.
    let cipher = Aes256Gcm::new(key);
    let nonce = GenericArray::from_slice(&encrypted_password.nonce[..]);
    let plaintext = cipher.decrypt(nonce, encrypted_password.ciphertext.as_ref())?;
    Ok(String::from_utf8(plaintext)?)
}
