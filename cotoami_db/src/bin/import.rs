use anyhow::Result;
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
