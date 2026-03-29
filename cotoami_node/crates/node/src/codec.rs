use serde::Serialize;

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
) -> Result<Vec<u8>, cotoami_db::rmp_serde::encode::Error> {
    let mut buf = Vec::new();
    let mut serializer = cotoami_db::rmp_serde::Serializer::new(&mut buf).with_struct_map();
    value.serialize(&mut serializer)?;
    Ok(buf)
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use serde::{Deserialize, Serialize};

    use super::to_msgpack_vec_named;

    #[test]
    fn msgpack_adding_a_defaulted_field_should_remain_backward_compatible() -> Result<()> {
        #[derive(Debug, Serialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV1 {
            SetImageMaxSize { size: i32 },
        }

        #[derive(Debug, Deserialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV2 {
            SetImageMaxSize {
                #[serde(default)]
                unit: Option<String>,
                size: i32,
            },
        }

        let bytes = to_msgpack_vec_named(&CommandSchemaV1::SetImageMaxSize { size: 1024 })?;

        let restored: CommandSchemaV2 = cotoami_db::rmp_serde::from_slice(&bytes)?;
        match restored {
            CommandSchemaV2::SetImageMaxSize { size, unit } => {
                assert_eq!(size, 1024);
                assert_eq!(unit, None);
            }
        }

        Ok(())
    }

    #[test]
    fn msgpack_reordering_fields_should_not_break_compatibility() -> Result<()> {
        #[derive(Debug, Serialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV1 {
            SearchCotos { query: String, only_cotonomas: bool },
        }

        #[derive(Debug, Deserialize)]
        #[serde(tag = "type", rename_all = "snake_case")]
        enum CommandSchemaV2 {
            SearchCotos { only_cotonomas: bool, query: String },
        }

        let bytes = to_msgpack_vec_named(&CommandSchemaV1::SearchCotos {
            query: "rust".into(),
            only_cotonomas: true,
        })?;

        let restored: CommandSchemaV2 = cotoami_db::rmp_serde::from_slice(&bytes)?;
        match restored {
            CommandSchemaV2::SearchCotos {
                only_cotonomas,
                query,
            } => {
                assert!(only_cotonomas);
                assert_eq!(query, "rust");
            }
        }

        Ok(())
    }
}
