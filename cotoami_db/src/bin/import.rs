use anyhow::{anyhow, Result};
use chrono::naive::NaiveDateTime;
use cotoami_db::prelude::*;

fn main() -> Result<()> {
    println!("Hello, world!");
    Ok(())
}

#[derive(Debug, serde::Deserialize)]
struct CotonomaJson {
    id: Id<Cotonoma>,

    name: String,
    key: String,

    shared: bool,

    // Perhaps this property isn't used in the old app,
    // I couldn't find usages in the source code :-(
    pinned: bool,

    timeline_revision: u32,
    graph_revision: u32,

    last_post_timestamp: i64, // epoch milliseconds

    inserted_at: i64, // epoch milliseconds
    updated_at: i64,  // epoch milliseconds
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

fn from_timestamp_millis(millis: i64) -> Result<NaiveDateTime> {
    NaiveDateTime::from_timestamp_millis(millis)
        .ok_or(anyhow!("The timestamp is out of range: {millis}"))
}

#[cfg(test)]
mod tests {
    use anyhow::Result;
    use indoc::indoc;

    use super::*;

    #[test]
    fn deserialize_cotonoma_json() -> Result<()> {
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
        println!("cotonoma: {cotonoma:?}");
        Ok(())
    }
}
