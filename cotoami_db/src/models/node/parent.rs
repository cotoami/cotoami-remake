use aes_gcm::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    Aes256Gcm, Key,
};
use anyhow::Result;
use argon2::Argon2;
use chrono::NaiveDateTime;
use diesel::{
    backend::Backend, deserialize::FromSql, expression::AsExpression, prelude::*, serialize::ToSql,
    sql_types::Binary, sqlite::Sqlite, FromSqlRow,
};
use generic_array::GenericArray;
use uuid::Uuid;
use validator::Validate;

use super::Node;
use crate::{models::Id, schema::parent_nodes};

/////////////////////////////////////////////////////////////////////////////
// ParentNode
/////////////////////////////////////////////////////////////////////////////

/// A row in `parent_nodes` table
#[derive(
    derive_more::Debug,
    Clone,
    Eq,
    PartialEq,
    Identifiable,
    AsChangeset,
    Queryable,
    Selectable,
    Validate,
)]
#[diesel(primary_key(node_id))]
pub struct ParentNode {
    /// UUID of this parent node
    pub node_id: Id<Node>,

    /// URL prefix to connect to this parent node
    #[validate(url, length(max = "ParentNode::URL_PREFIX_MAX_LENGTH"))]
    pub url_prefix: String,

    pub created_at: NaiveDateTime,

    /// Saved password to connect to this parent node
    #[debug(skip)]
    pub encrypted_password: Option<EncryptedPassword>,

    /// Number of changes received from this parent node
    pub changes_received: i64,

    /// Date when received the last change from this parent node
    pub last_change_received_at: Option<NaiveDateTime>,

    /// Local node won't connect to this parent node if the value is TRUE
    pub disabled: bool,
}

impl ParentNode {
    // 2000 characters minus 500 for an API path after the prefix
    // cf. https://stackoverflow.com/a/417184
    pub const URL_PREFIX_MAX_LENGTH: usize = 1500;

    pub fn save_password(&mut self, plaintext: &str, encryption_password: &str) -> Result<()> {
        self.encrypted_password = Some(encrypt_password(plaintext, encryption_password)?);
        Ok(())
    }

    pub fn password(&self, encryption_password: &str) -> Result<Option<String>> {
        if let Some(ref encrypted_password) = self.encrypted_password {
            let plaintext = decrypt_password(encrypted_password, encryption_password)?;
            Ok(Some(plaintext))
        } else {
            Ok(None)
        }
    }
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
// ClearParentPassword
/////////////////////////////////////////////////////////////////////////////

/// Changeset to set NULL to [parent_nodes::encrypted_password].
///
/// I couldn't find a straightforward way to set NULL to a field in UPDATE dsl.
/// In the document (<https://diesel.rs/guides/all-about-updates.html>), the only way
/// introduced is to use an [AsChangeset] struct with `#[diesel(treat_none_as_null = true)]`
/// or to have the field be of type `Option<Option<T>>`.
#[derive(AsChangeset)]
#[diesel(table_name = parent_nodes, treat_none_as_null = true)]
pub struct ClearParentPassword {
    encrypted_password: Option<EncryptedPassword>,
}

impl ClearParentPassword {
    pub fn new() -> Self {
        Self {
            encrypted_password: None,
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// NewParentNode
/////////////////////////////////////////////////////////////////////////////

/// An `Insertable` parent node data
#[derive(Insertable, Validate)]
#[diesel(table_name = parent_nodes)]
pub struct NewParentNode<'a> {
    node_id: &'a Id<Node>,
    #[validate(url, length(max = "ParentNode::URL_PREFIX_MAX_LENGTH"))]
    url_prefix: &'a str,
    created_at: NaiveDateTime,
}

impl<'a> NewParentNode<'a> {
    pub fn new(node_id: &'a Id<Node>, url_prefix: &'a str) -> Result<Self> {
        let parent_node = Self {
            node_id,
            url_prefix,
            created_at: crate::current_datetime(),
        };
        parent_node.validate()?;
        Ok(parent_node)
    }
}

/////////////////////////////////////////////////////////////////////////////
// Internal functions
/////////////////////////////////////////////////////////////////////////////

fn encrypt_password(plaintext: &str, encryption_password: &str) -> Result<EncryptedPassword> {
    // Generate a salt
    // A random salt is fine for that as long as its length is sufficient;
    // a 16-byte salt would work well (by definition, UUID are very good salts).
    // https://docs.rs/password-hash/0.5.0/password_hash/struct.Salt.html
    let salt = Uuid::new_v4().into_bytes();

    // Transform an `encryption_password` into an encryption key
    let mut key = [0u8; 32];
    Argon2::default().hash_password_into(encryption_password.as_bytes(), &salt, &mut key)?;
    let key: &Key<Aes256Gcm> = &key.into();

    // Encrypt the password
    let cipher = Aes256Gcm::new(&key);
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
    // Transform an `encryption_password` into an encryption key
    let mut key = [0u8; 32];
    Argon2::default().hash_password_into(
        encryption_password.as_bytes(),
        &encrypted_password.salt,
        &mut key,
    )?;
    let key: &Key<Aes256Gcm> = &key.into();

    // Decrypt the password
    let cipher = Aes256Gcm::new(&key);
    let nonce = GenericArray::from_slice(&encrypted_password.nonce[..]);
    let plaintext = cipher.decrypt(&nonce, encrypted_password.ciphertext.as_ref())?;
    Ok(String::from_utf8(plaintext)?)
}
