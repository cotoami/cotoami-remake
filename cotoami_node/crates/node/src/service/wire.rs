//! Transport-level schemas for the node service protocol.
//!
//! This module defines the stable wire contract used when service requests are
//! serialized and deserialized for communication between cotoami nodes. These
//! schema types are intentionally separate from the internal domain types such
//! as [`super::Command`] and [`super::Request`].
//!
//! The separation is important because internal service types are free to
//! evolve for implementation reasons, while the wire protocol needs a more
//! deliberate compatibility policy. If node-to-node communication serialized
//! the internal enums and structs directly, ordinary refactors such as changing
//! variant layouts, reordering fields, or replacing borrowed data with owned
//! data could accidentally break interoperability between versions.
//!
//! The role of this module is therefore:
//!
//! - to define explicit transport-facing schema types with stable names
//! - to decouple the external protocol from in-process service/domain design
//! - to centralize compatibility decisions in one place
//! - to let multiple encodings such as JSON and MessagePack share the same
//!   logical request/command schema
//!
//! The typical flow is:
//!
//! - outgoing: internal service type -> schema type -> serialized bytes
//! - incoming: serialized bytes -> schema type -> internal service type
//!
//! `RequestSchema` is the root service schema for node-to-node requests, while
//! `schemas::*` currently contains the dedicated transport schema for commands.
//! Nested payload structs are reused directly unless and until their transport
//! representation needs to diverge from the internal type.
//!
//! For MessagePack specifically, these schemas are intended to be serialized
//! with named struct fields via [`to_msgpack_vec_named`] so that field-order
//! changes and additive fields can evolve more safely than with the default
//! compact positional encoding.

use cotoami_db::rmp_serde;
use serde::{Deserialize, Deserializer, Serialize, Serializer};
use uuid::Uuid;

use crate::service::{Request, SerializeFormat};

pub mod schemas;

pub use self::schemas::*;

/// Serialize a value into MessagePack while preserving field names for structs.
///
/// This helper exists for transport-level payloads that need schema evolution.
/// `rmp_serde::to_vec` prefers a compact positional representation for structs,
/// which means a struct may be encoded like an array of fields in declaration
/// order instead of a map keyed by field name. That compact form is efficient,
/// but it is fragile for long-lived protocols:
///
/// - reordering fields can break deserialization
/// - inserting a new field before existing ones can break deserialization
/// - `#[serde(default)]` and optional fields cannot reliably help if the data is
///   matched by position rather than by field name
///
/// By forcing struct-map encoding, this function writes MessagePack objects with
/// explicit field names. That makes the wire format much more tolerant to normal
/// schema evolution such as adding defaulted fields or reordering fields without
/// changing their names.
///
/// This should be preferred over plain `rmp_serde::to_vec` for node-to-node wire
/// contracts and any other serialized data that must remain compatible across
/// versions. For short-lived internal payloads where compactness matters more
/// than evolution safety, plain `rmp_serde::to_vec` may still be appropriate.
pub fn to_msgpack_vec_named<T: Serialize>(
    value: &T,
) -> Result<Vec<u8>, rmp_serde::encode::Error> {
    let mut buf = Vec::new();
    let mut serializer = rmp_serde::Serializer::new(&mut buf).with_struct_map();
    value.serialize(&mut serializer)?;
    Ok(buf)
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct RequestSchema {
    id: Uuid,
    accept: SerializeFormat,
    #[serde(default)]
    as_owner: bool,
    command: CommandSchema,
}

impl Serialize for Request {
    fn serialize<S>(&self, serializer: S) -> Result<S::Ok, S::Error>
    where
        S: Serializer,
    {
        RequestSchema::from(self).serialize(serializer)
    }
}

impl<'de> Deserialize<'de> for Request {
    fn deserialize<D>(deserializer: D) -> Result<Self, D::Error>
    where
        D: Deserializer<'de>,
    {
        RequestSchema::deserialize(deserializer).map(Into::into)
    }
}

impl From<&Request> for RequestSchema {
    fn from(request: &Request) -> Self {
        Self {
            id: request.id,
            accept: request.accept,
            as_owner: request.as_owner,
            command: request.command.clone().into(),
        }
    }
}

impl From<RequestSchema> for Request {
    fn from(schema: RequestSchema) -> Self {
        Self {
            id: schema.id,
            from: None,
            accept: schema.accept,
            as_owner: schema.as_owner,
            command: schema.command.into(),
        }
    }
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use serde_json::json;

    use super::*;
    use crate::service::{models::Pagination, Command};
    use cotoami_db::prelude::*;

    #[test]
    fn request_json_uses_explicit_wire_schema() -> Result<()> {
        let cotonoma_id = Id::generate();
        let request = Request {
            id: Uuid::nil(),
            from: None,
            accept: SerializeFormat::Json,
            as_owner: false,
            command: Command::PostCoto {
                input: CotoInput::new("hello").summary("summary"),
                post_to: cotonoma_id,
            },
        };

        let value = serde_json::to_value(&request)?;

        assert_eq!(
            value,
            json!({
                "id": Uuid::nil(),
                "accept": "Json",
                "as_owner": false,
                "command": {
                    "type": "post_coto",
                    "input": {
                        "content": "hello",
                        "summary": "summary",
                        "media_content": null,
                        "geolocation": null,
                        "datetime_range": null
                    },
                    "post_to": cotonoma_id
                }
            })
        );

        Ok(())
    }

    #[test]
    fn request_json_roundtrip_restores_internal_command() -> Result<()> {
        let coto_id = Id::generate();
        let request = Request {
            id: Uuid::nil(),
            from: None,
            accept: SerializeFormat::MessagePack,
            as_owner: true,
            command: Command::SearchCotos {
                query: "query".into(),
                scope: Scope::Cotonoma((coto_id, CotonomaScope::Depth(3))),
                only_cotonomas: true,
                pagination: Pagination {
                    page: 2,
                    page_size: Some(20),
                },
            },
        };

        let json = serde_json::to_vec(&request)?;
        let restored: Request = serde_json::from_slice(&json)?;

        assert_eq!(restored.id(), request.id());
        assert!(matches!(restored.accept(), SerializeFormat::MessagePack));
        assert!(restored.as_owner());
        match restored.command() {
            Command::SearchCotos {
                query,
                scope,
                only_cotonomas,
                pagination,
            } => {
                assert_eq!(query, "query");
                assert_eq!(scope, Scope::Cotonoma((coto_id, CotonomaScope::Depth(3))));
                assert!(only_cotonomas);
                assert_eq!(pagination.page, 2);
                assert_eq!(pagination.page_size, Some(20));
            }
            other => panic!("unexpected command after roundtrip: {other:?}"),
        }

        Ok(())
    }

    #[test]
    fn request_message_pack_roundtrip_restores_internal_command() -> Result<()> {
        let source_coto = Id::generate();
        let post_to = Id::generate();
        let request = Request {
            id: Uuid::nil(),
            from: None,
            accept: SerializeFormat::MessagePack,
            as_owner: false,
            command: Command::PostSubcoto {
                source_coto,
                input: CotoInput::new("child"),
                post_to: Some(post_to),
                order: Some(4),
            },
        };

        let bytes = to_msgpack_vec_named(&request)?;
        let restored: Request = cotoami_db::rmp_serde::from_slice(&bytes)?;

        match restored.command() {
            Command::PostSubcoto {
                source_coto: actual_source_coto,
                input,
                post_to: actual_post_to,
                order,
            } => {
                assert_eq!(actual_source_coto, source_coto);
                assert_eq!(input.content.as_ref(), "child");
                assert_eq!(actual_post_to, Some(post_to));
                assert_eq!(order, Some(4));
            }
            other => panic!("unexpected command after roundtrip: {other:?}"),
        }

        Ok(())
    }

}
