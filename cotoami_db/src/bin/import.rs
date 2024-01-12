use std::{
    collections::HashSet, env, fmt::Display, fs, fs::File, io::BufReader, path::Path, time::Instant,
};

use anyhow::{anyhow, Result};
use chrono::naive::NaiveDateTime;
use cotoami_db::prelude::*;

fn main() -> Result<()> {
    let config = Config::new(env::args())?;

    let db = config.db()?;
    let json = config.load_json()?;

    let start = Instant::now();
    import(db, json)?;
    println!("Import completed - elapsed {:?}.", start.elapsed());

    Ok(())
}

struct Config {
    json_file: String,
    db_dir: String,
    new_node_name: Option<String>,
}

impl Config {
    const USAGE: &'static str = "Usage: import <JSON file> <database dir> (<new node name>)";

    fn new(mut args: env::Args) -> Result<Config> {
        args.next(); // skip the first arg (the name of the program)

        let json_file = args
            .next()
            .ok_or(anyhow!("Please specify a JSON file. \n{}", Self::USAGE))?;
        let db_dir = args.next().ok_or(anyhow!(
            "Please specify a database directory. \n{}",
            Self::USAGE
        ))?;
        let new_node_name = args.next();

        Ok(Config {
            json_file,
            db_dir,
            new_node_name,
        })
    }

    fn load_json(&self) -> Result<CotoamiExportJson> { CotoamiExportJson::load(&self.json_file) }

    fn db(&self) -> Result<Database> {
        // Create the directory if it doesn't exist yet (a new database).
        fs::create_dir(&self.db_dir).ok();

        let db = Database::new(&self.db_dir)?;

        // Create a local node with the given name if it doesn't exist yet.
        if !db.globals().has_local_node_initialized() {
            let node_name = self.new_node_name.as_deref().ok_or(anyhow!(
                "Please specify a new node name to create a new database. \n{}",
                Self::USAGE
            ))?;
            println!("Creating a local node [{node_name}] ...");
            let _ = db.new_session()?.init_as_node(Some(node_name), None)?;
        }

        Ok(db)
    }
}

struct Context {
    all_coto_ids: HashSet<Id<Coto>>,
    all_cotonoma_ids: HashSet<Id<Cotonoma>>,
    local_node_id: Id<Node>,
    root_cotonoma_id: Id<Cotonoma>,
}

impl Context {
    fn contains_coto(&self, id: &Id<Coto>) -> bool { self.all_coto_ids.contains(id) }

    fn contains_cotonoma(&self, id: &Id<Cotonoma>) -> bool { self.all_cotonoma_ids.contains(id) }

    fn reject(&mut self, coto_json: &CotoJson, reason: &str) {
        self.all_coto_ids.remove(&coto_json.id);
        if let Some(cotonoma_json) = coto_json.cotonoma.as_ref() {
            self.all_cotonoma_ids.remove(&cotonoma_json.id);
        }
        println!("Rejected Coto ({}): {reason}", coto_json.id);
    }
}

fn import(db: Database, json: CotoamiExportJson) -> Result<()> {
    let mut context = Context {
        all_coto_ids: json.all_coto_ids(),
        all_cotonoma_ids: json.all_cotonoma_ids(),
        local_node_id: db.globals().local_node_id()?,
        root_cotonoma_id: db
            .globals()
            .root_cotonoma_id()
            .ok_or(anyhow!("The root cotonoma is required for import."))?,
    };
    println!(
        "Importing {} cotos, {} cotonomas ...",
        context.all_coto_ids.len(),
        context.all_cotonoma_ids.len()
    );
    let mut ds = db.new_session()?;
    import_cotos(&mut ds, json.cotos, &mut context)?;
    Ok(())
}

fn import_cotos(
    ds: &mut DatabaseSession<'_>,
    coto_jsons: Vec<CotoJson>,
    context: &mut Context,
) -> Result<()> {
    let mut pendings: Vec<CotoJson> = Vec::new();
    for coto_json in coto_jsons {
        // Dependency check: `posted_in_id`
        if let Some(posted_in_id) = coto_json.posted_in_id {
            if ds.contains_cotonoma(&posted_in_id)? {
                // OK
            } else {
                if context.contains_cotonoma(&posted_in_id) {
                    // Put in the pending list until the cotonoma is imported
                    pendings.push(coto_json);
                } else {
                    context.reject(&coto_json, &format!("Missing cotonoma: {posted_in_id}."));
                }
                continue;
            }
        }

        // Dependency check: `repost_id`
        if let Some(repost_id) = coto_json.repost_id {
            if ds.contains_coto(&repost_id)? {
                // OK
            } else {
                if context.contains_coto(&repost_id) {
                    // Put in the pending list until the original coto is imported
                    pendings.push(coto_json);
                } else {
                    context.reject(
                        &coto_json,
                        &format!("Repost of a missing coto: {repost_id}."),
                    );
                }
                continue;
            }
        }

        import_coto(ds, coto_json, context)?;
    }
    if pendings.is_empty() {
        Ok(())
    } else {
        import_cotos(ds, pendings, context)
    }
}

fn import_coto(
    ds: &mut DatabaseSession<'_>,
    mut coto_json: CotoJson,
    context: &mut Context,
) -> Result<()> {
    if ds.contains_coto(&coto_json.id)? {
        context.reject(&coto_json, "Already exists in the db.");
    } else {
        let cotonoma_json = coto_json.cotonoma.take();

        let mut coto = coto_json.into_coto(context.local_node_id)?;
        if coto.posted_in_id.is_none() {
            // A coto that doesn't belong to a cotonoma will be imported in the root cotonoma.
            coto.posted_in_id = Some(context.root_cotonoma_id);
        }

        if let Some(cotonoma_json) = cotonoma_json {
            let cotonoma = cotonoma_json.into_cotonoma(context.local_node_id, coto.uuid)?;
            let _ = ds.import_cotonoma(&coto, &cotonoma)?;
        } else {
            let _ = ds.import_coto(&coto)?;
        }
    }
    Ok(())
}

fn from_timestamp_millis(millis: i64) -> Result<NaiveDateTime> {
    NaiveDateTime::from_timestamp_millis(millis)
        .ok_or(anyhow!("The timestamp is out of range: {millis}"))
}

/////////////////////////////////////////////////////////////////////////////
// CotoamiExportJson
/////////////////////////////////////////////////////////////////////////////

/// Exported JSON from the original Cotoami.
/// <https://github.com/cotoami/cotoami/blob/develop/lib/cotoami_web/controllers/database_controller.ex#L12>
#[derive(Debug, serde::Deserialize)]
struct CotoamiExportJson {
    cotos: Vec<CotoJson>,
    connections: Vec<ConnectionJson>,
}

impl CotoamiExportJson {
    fn load<P: AsRef<Path> + Display>(path: P) -> Result<Self> {
        println!("Parsing {path} ...");
        let file = File::open(path)?;
        let reader = BufReader::new(file);
        Ok(serde_json::from_reader(reader)?)
    }

    fn all_coto_ids(&self) -> HashSet<Id<Coto>> { self.cotos.iter().map(|coto| coto.id).collect() }

    fn all_cotonoma_ids(&self) -> HashSet<Id<Cotonoma>> {
        self.cotos
            .iter()
            .filter_map(|coto| coto.cotonoma.as_ref().map(|cotonoma| cotonoma.id))
            .collect()
    }
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

    // `None` if this coto doesn't belong to any cotonoma, which is displayed only in "My Home".
    // In the original Cotoami, "My Home" means "no cotonoma specified".
    // On the other hand, in Cotoami Remake, that means being posted in the root cotonoma.
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
#[allow(unused)]
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
    last_post_timestamp: Option<i64>, // epoch milliseconds
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
#[allow(unused)]
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
    use std::str::FromStr;

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
            }
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

    #[test]
    fn deserialize_connection_json() -> Result<()> {
        let node_id: Id<Node> = Id::from_str("00000000-0000-0000-0000-000000000001")?;
        let json = indoc! {r#"
            {
                "start": "f05c0f03-8bb0-430e-a4d2-714c2922e0cd",
                "order": 1,
                "end": "72972fe8-695c-4086-86ff-29a12c8a98a4",
                "created_by": "55111bd3-92e2-4b02-bc1a-15b74a945fd0",
                "created_at": 1507106701180
            }
        "#};
        let conn: ConnectionJson = serde_json::from_str(json)?;
        let _ = conn.as_new_link(&node_id)?;

        assert_eq!(
            conn.start,
            Id::from_str("f05c0f03-8bb0-430e-a4d2-714c2922e0cd")?
        );
        assert_eq!(
            conn.end,
            Id::from_str("72972fe8-695c-4086-86ff-29a12c8a98a4")?
        );
        assert_eq!(conn.order, 1);
        assert_eq!(conn.linking_phrase, None);

        Ok(())
    }
}
