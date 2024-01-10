use std::str::FromStr;

use anyhow::{anyhow, Result};
use chrono::naive::NaiveDateTime;
use cotoami_db::prelude::*;

fn main() -> Result<()> {
    println!("Hello, world!");
    Ok(())
}

fn from_timestamp_millis(millis: i64) -> Result<NaiveDateTime> {
    NaiveDateTime::from_timestamp_millis(millis)
        .ok_or(anyhow!("The timestamp is out of range: {millis}"))
}

/////////////////////////////////////////////////////////////////////////////
// CotoJson
/////////////////////////////////////////////////////////////////////////////

/// Exported coto JSON from the original Cotoami.
/// <https://github.com/cotoami/cotoami/blob/develop/lib/cotoami_web/views/coto_view.ex#L48-L61>
#[derive(Debug, serde::Deserialize)]
struct CotoJson {
    id: Id<Coto>,

    content: Option<String>,
    summary: Option<String>,

    posted_in_id: Option<Id<Cotonoma>>,

    as_cotonoma: bool,
    cotonoma: Option<CotonomaJson>,

    repost_id: Option<Id<Coto>>,
    reposted_in_ids: Vec<Id<Cotonoma>>,

    inserted_at: i64, // epoch milliseconds
    updated_at: i64,  // epoch milliseconds
}

impl CotoJson {
    fn into_coto(self, node_id: Id<Node>) -> Result<Coto> {
        let reposted_in_ids = if self.reposted_in_ids.is_empty() {
            None
        } else {
            Some(Ids(self.reposted_in_ids))
        };
        Ok(Coto {
            uuid: self.id,
            rowid: 0,
            node_id,
            posted_in_id: self.posted_in_id,
            posted_by_id: node_id,
            content: self.content,
            summary: self.summary,
            is_cotonoma: self.as_cotonoma,
            repost_of_id: self.repost_id,
            reposted_in_ids,
            created_at: from_timestamp_millis(self.inserted_at)?,
            updated_at: from_timestamp_millis(self.updated_at)?,
            outgoing_links: 0,
        })
    }
}

/////////////////////////////////////////////////////////////////////////////
// CotonomaJson
/////////////////////////////////////////////////////////////////////////////

/// Exported cotonoma JSON from the original Cotoami.
/// <https://github.com/cotoami/cotoami/blob/develop/lib/cotoami_web/views/cotonoma_view.ex#L61-L74>
#[derive(Debug, serde::Deserialize)]
struct CotonomaJson {
    id: Id<Cotonoma>,

    key: String,
    name: String,

    shared: bool,

    // Perhaps this property isn't used in the original Cotoami,
    // I couldn't find usages in the source code :-(
    pinned: bool,

    // The revisions seem to be used only for detecting changes since the cotonoma is created:
    // <https://github.com/cotoami/cotoami/blob/develop/assets/elm/src/App/Types/Coto.elm#L244>
    timeline_revision: u32,
    graph_revision: u32,

    inserted_at: i64, // epoch milliseconds
    updated_at: i64,  // epoch milliseconds

    // This property is used for read/unread status management with the watchlist table:
    // <https://github.com/cotoami/cotoami/blob/develop/priv/repo/migrations/20181101015822_create_watchlist.exs>
    //
    // Read/unread status management in the client-side:
    // <https://github.com/cotoami/cotoami/blob/develop/assets/elm/src/App/Update/Watch.elm>
    // <https://github.com/cotoami/cotoami/blob/develop/assets/elm/src/App/Views/Flow.elm#L480-L486>
    last_post_timestamp: i64, // epoch milliseconds
}

impl CotonomaJson {
    fn into_cotonoma(self, node_id: Id<Node>, coto_id: Id<Coto>) -> Result<Cotonoma> {
        Ok(Cotonoma {
            uuid: self.id,
            node_id,
            coto_id,
            name: self.name,
            created_at: from_timestamp_millis(self.inserted_at)?,
            updated_at: from_timestamp_millis(self.updated_at)?,
            posts: 0,
        })
    }
}

/////////////////////////////////////////////////////////////////////////////
// ConnectionJson
/////////////////////////////////////////////////////////////////////////////

/// Exported connection JSON from the original Cotoami.
/// <https://github.com/cotoami/cotoami/blob/develop/lib/cotoami/services/coto_graph_service.ex#L222>
#[derive(Debug, serde::Deserialize)]
struct ConnectionJson {
    start: Id<Coto>,
    end: Id<Coto>,

    created_by: String, // amishi_id
    created_in: Option<Id<Cotonoma>>,

    linking_phrase: Option<String>,

    order: i32,

    created_at: i64, // epoch milliseconds
}

impl ConnectionJson {
    fn as_new_link<'a>(&'a self, node_id: &'a Id<Node>) -> Result<NewLink<'a>> {
        Ok(NewLink::new(
            node_id,
            self.created_in.as_ref(),
            node_id,
            &self.start,
            &self.end,
            self.linking_phrase.as_deref(),
            None,
            Some(self.order),
        )?)
    }
}

/////////////////////////////////////////////////////////////////////////////
// tests
/////////////////////////////////////////////////////////////////////////////

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use indoc::indoc;

    use super::*;

    #[test]
    fn deserialize_coto_json() -> Result<()> {
        let node_id: Id<Node> = Id::from_str("00000000-0000-0000-0000-000000000001")?;
        let json = indoc! {r#"
            {
                "updated_at": 1507106650888,
                "summary": null,
                "reposted_in_ids": [],
                "repost_id": null,
                "posted_in_id": null,
                "inserted_at": 1507106650888,
                "id": "f05c0f03-8bb0-430e-a4d2-714c2922e0cd",
                "cotonoma": null,
                "content": "Nginx Ingress Controller",
                "as_cotonoma": false
            }
        "#};
        let coto: CotoJson = serde_json::from_str(json)?;
        let coto = coto.into_coto(node_id)?;

        assert_eq!(
            coto.uuid,
            Id::from_str("f05c0f03-8bb0-430e-a4d2-714c2922e0cd")?
        );
        assert_eq!(coto.content, Some("Nginx Ingress Controller".into()));
        assert_eq!(coto.summary, None);
        assert_eq!(coto.repost_of_id, None);
        assert_eq!(coto.reposted_in_ids, None);
        assert_eq!(coto.created_at.to_string(), "2017-10-04 08:44:10.888");
        assert_eq!(coto.updated_at.to_string(), "2017-10-04 08:44:10.888");

        Ok(())
    }

    #[test]
    fn deserialize_coto_json2() -> Result<()> {
        let node_id: Id<Node> = Id::from_str("00000000-0000-0000-0000-000000000001")?;
        let json = indoc! {r#"
            {
                "updated_at": 1561866119804,
                "summary": null,
                "reposted_in_ids": [
                    "a09327c2-d3d8-400e-b7ae-6af110fa982e",
                    "e0871a62-33d0-407c-bdc0-9d72c6c84de4"
                ],
                "repost_id": null,
                "posted_in_id": null,
                "inserted_at": 1530275609616,
                "id": "4d993f1c-270e-457a-9d12-92201e998691",
                "cotonoma": null,
                "content": "「最大多数の最小不幸」原則",
                "as_cotonoma": false
            },
        "#};
        let coto: CotoJson = serde_json::from_str(json)?;
        let coto = coto.into_coto(node_id)?;

        assert_eq!(
            coto.uuid,
            Id::from_str("4d993f1c-270e-457a-9d12-92201e998691")?
        );
        assert_eq!(coto.content, Some("「最大多数の最小不幸」原則".into()));
        assert_eq!(coto.summary, None);
        assert_eq!(coto.repost_of_id, None);
        assert_eq!(
            coto.reposted_in_ids,
            Some(Ids(vec![
                Id::from_str("a09327c2-d3d8-400e-b7ae-6af110fa982e")?,
                Id::from_str("e0871a62-33d0-407c-bdc0-9d72c6c84de4")?
            ]))
        );

        Ok(())
    }

    #[test]
    fn deserialize_cotonoma_json() -> Result<()> {
        let node_id: Id<Node> = Id::from_str("00000000-0000-0000-0000-000000000001")?;
        let coto_id: Id<Coto> = Id::from_str("00000000-0000-0000-0000-000000000002")?;
        let json = indoc! {r#"
            {
                "updated_at": 1681253566558,
                "timeline_revision": 102,
                "shared": false,
                "pinned": true,
                "name": "Cotoami",
                "last_post_timestamp": 1561866275044,
                "key": "2al3mr9qljoslr23",
                "inserted_at": 1507132300379,
                "id": "43dea0e3-f19b-4837-8587-7ed55296c265",
                "graph_revision": 0
            }
        "#};
        let cotonoma: CotonomaJson = serde_json::from_str(json)?;
        let cotonoma = cotonoma.into_cotonoma(node_id, coto_id)?;

        assert_eq!(
            cotonoma.uuid,
            Id::from_str("43dea0e3-f19b-4837-8587-7ed55296c265")?
        );
        assert_eq!(cotonoma.name, "Cotoami");
        assert_eq!(cotonoma.node_id, node_id);
        assert_eq!(cotonoma.coto_id, coto_id);
        assert_eq!(cotonoma.created_at.to_string(), "2017-10-04 15:51:40.379");
        assert_eq!(cotonoma.updated_at.to_string(), "2023-04-11 22:52:46.558");

        Ok(())
    }
}
