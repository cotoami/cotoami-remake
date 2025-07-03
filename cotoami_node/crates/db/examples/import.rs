//! A CLI tool to import an original Cotoami's JSON dump.
//!
//! The export feature in the original Cotoami is implemented as:
//! <https://github.com/cotoami/cotoami/blob/develop/lib/cotoami_web/controllers/database_controller.ex#L12>
//!
//! How to use this tool via cargo:
//! ```shell
//! $ cargo run --example import /path/to/cotoami-export.json /path/to/db-dir "New node name"
//! ```

use std::{
    collections::HashSet, fmt::Display, fs, fs::File, io::BufReader, path::Path, time::Instant,
};

use anyhow::{anyhow, bail, Result};
use chrono::{naive::NaiveDateTime, DateTime};
use clap::Parser;
use cotoami_db::prelude::*;
use uuid::Uuid;

fn main() -> Result<()> {
    let args = Args::parse();

    let db = args.db()?;
    let json = args.load_json()?;

    let start = Instant::now();
    import(db, json, args.exclude_cotonoma)?;
    println!("Import completed - elapsed {:?}.", start.elapsed());

    Ok(())
}

#[derive(Parser, Debug)]
#[command(author, version)]
#[command(about = "Import an original Cotoami's JSON dump.", long_about = None)]
struct Args {
    json_file: String,
    db_dir: String,
    new_node_name: Option<String>,

    #[arg(long)]
    exclude_cotonoma: Vec<String>,
}

impl Args {
    fn load_json(&self) -> Result<CotoamiExportJson> { CotoamiExportJson::load(&self.json_file) }

    fn db(&self) -> Result<Database> {
        // Create the directory if it doesn't exist yet (a new database).
        fs::create_dir(&self.db_dir).ok();

        let db = Database::new(&self.db_dir)?;

        // Create a local node with the given name if it doesn't exist yet.
        if !db.globals().has_local_node() {
            let node_name = self.new_node_name.as_deref().ok_or(anyhow!(
                "Please specify a [NEW_NODE_NAME] to create a new database."
            ))?;
            println!("Creating a local node [{node_name}] ...");
            let _ = db.new_session()?.init_as_node(Some(node_name), None)?;
        }

        Ok(db)
    }
}

#[derive(Debug)]
struct Context {
    local_node_id: Id<Node>,
    root_cotonoma: Cotonoma,
    exclude_cotonoma: Vec<String>,

    coto_waitlist: HashSet<Id<Coto>>,
    cotonoma_waitlist: HashSet<Id<Cotonoma>>,

    imported_cotos: i64,
    imported_cotonomas: i64,
    imported_connections: i64,

    rejected_cotos: i64,
    rejected_connections: i64,
}

impl Context {
    fn new(
        local_node_id: Id<Node>,
        root_cotonoma: Cotonoma,
        exclude_cotonoma: Vec<String>,
        coto_waitlist: HashSet<Id<Coto>>,
        cotonoma_waitlist: HashSet<Id<Cotonoma>>,
    ) -> Self {
        Self {
            local_node_id,
            root_cotonoma,
            exclude_cotonoma,
            coto_waitlist,
            cotonoma_waitlist,
            imported_cotos: 0,
            imported_cotonomas: 0,
            imported_connections: 0,
            rejected_cotos: 0,
            rejected_connections: 0,
        }
    }

    fn should_exclude(&self, coto_json: &CotoJson) -> bool {
        if let Some(ref posted_in_id) = coto_json.posted_in_id {
            if self.exclude_cotonoma.contains(&posted_in_id.to_string()) {
                return true;
            }
        }
        if let Some(ref cotonoma) = coto_json.cotonoma {
            if self.exclude_cotonoma.contains(&cotonoma.id.to_string()) {
                return true;
            }
        }
        false
    }

    fn has_coto_in_waitlist(&self, id: &Id<Coto>) -> bool { self.coto_waitlist.contains(id) }

    fn has_cotonoma_in_waitlist(&self, id: &Id<Cotonoma>) -> bool {
        self.cotonoma_waitlist.contains(id)
    }

    fn on_coto_imported(&mut self) { self.imported_cotos += 1; }
    fn on_coto_cotonoma_imported(&mut self) {
        self.imported_cotos += 1;
        self.imported_cotonomas += 1;
    }
    fn on_connection_imported(&mut self) { self.imported_connections += 1; }

    fn remove_from_waitlist(&mut self, coto_json: &CotoJson) {
        self.coto_waitlist.remove(&coto_json.id);
        if let Some(cotonoma_json) = coto_json.cotonoma.as_ref() {
            self.cotonoma_waitlist.remove(&cotonoma_json.id);
        }
    }

    fn reject_coto(&mut self, coto_json: &CotoJson, reason: &str) {
        self.remove_from_waitlist(coto_json);
        self.rejected_cotos += 1;
        println!("Rejected coto ({}): {reason}", coto_json.name_or_id());
    }

    fn reject_connection(&mut self, connection_json: &ConnectionJson, reason: &str) {
        self.rejected_connections += 1;
        println!(
            "Rejected connection ({})->({}): {reason}",
            connection_json.start, connection_json.end
        );
    }
}

fn import(db: Database, json: CotoamiExportJson, exclude_cotonoma: Vec<String>) -> Result<()> {
    let mut ds = db.new_session()?;

    // Init a context
    let Some((root_cotonoma, _)) = ds.local_node_root()? else {
        bail!("The root cotonoma is required for import.")
    };
    let mut context = Context::new(
        db.globals().try_get_local_node_id()?,
        root_cotonoma,
        exclude_cotonoma,
        json.all_coto_ids(),
        json.all_cotonoma_ids(),
    );
    println!(
        "Importing {} cotos, {} cotonomas and {} connections ...",
        context.coto_waitlist.len(),
        context.cotonoma_waitlist.len(),
        json.connections.len()
    );

    // Import
    import_cotos(&mut ds, json.cotos, &mut context)?;
    import_connections(&mut ds, json.connections, &mut context)?;

    println!(
        "Imported {} cotos, {} cotonomas and {} connections.",
        context.imported_cotos, context.imported_cotonomas, context.imported_connections
    );
    println!(
        "Rejected {} cotos and {} connections.",
        context.rejected_cotos, context.rejected_connections
    );

    Ok(())
}

fn import_cotos(
    ds: &mut DatabaseSession<'_>,
    coto_jsons: Vec<CotoJson>,
    context: &mut Context,
) -> Result<()> {
    println!("Importing cotos ...");
    let mut pendings: Vec<CotoJson> = Vec::new();
    for mut coto_json in coto_jsons {
        // Exclude by cotonoma
        if context.should_exclude(&coto_json) {
            context.reject_coto(&coto_json, "excluded");
            continue;
        }

        // Dependency check: `posted_in_id`
        if let Some(posted_in_id) = coto_json.posted_in_id {
            if ds.contains_cotonoma(&posted_in_id)? {
                // OK
            } else {
                if context.has_cotonoma_in_waitlist(&posted_in_id) {
                    // Put in the pending list until the cotonoma is imported
                    pendings.push(coto_json);
                } else {
                    context.reject_coto(&coto_json, &format!("missing cotonoma: {posted_in_id}"));
                }
                continue;
            }
        }

        // Dependency check: `repost_id`
        if let Some(repost_id) = coto_json.repost_id {
            if let Some(original) = ds.coto(&repost_id)? {
                // OK
                // Sync repost's update timestamp with the original
                coto_json.updated_at = std::cmp::max(
                    coto_json.updated_at,
                    original.updated_at.and_utc().timestamp_millis(),
                );
            } else {
                if context.has_coto_in_waitlist(&repost_id) {
                    // Put in the pending list until the original coto is imported
                    pendings.push(coto_json);
                } else {
                    context.reject_coto(
                        &coto_json,
                        &format!("repost of a missing coto: {repost_id}"),
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
        context.reject_coto(&coto_json, "already exists in the db.");
    } else {
        context.remove_from_waitlist(&coto_json);
        let cotonoma_json = coto_json.cotonoma.take();

        let mut coto = coto_json.into_coto(context.local_node_id)?;
        if coto.posted_in_id.is_none() {
            // A coto that doesn't belong to a cotonoma will be imported in the root cotonoma.
            coto.posted_in_id = Some(context.root_cotonoma.uuid);
        }

        if let Some(cotonoma_json) = cotonoma_json {
            let cotonoma = cotonoma_json.into_cotonoma(context.local_node_id, coto.uuid)?;
            let _ = ds.import_cotonoma(&coto, &cotonoma)?;
            context.on_coto_cotonoma_imported();
        } else {
            let _ = ds.import_coto(&coto)?;
            context.on_coto_imported();
        }
    }
    Ok(())
}

fn import_connections(
    ds: &mut DatabaseSession<'_>,
    connection_jsons: Vec<ConnectionJson>,
    context: &mut Context,
) -> Result<()> {
    println!("Importing connections ...");
    for conn_json in connection_jsons {
        if let Some(start_coto) = conn_json.start_as_coto() {
            if !ds.contains_coto(&start_coto)? {
                context
                    .reject_connection(&conn_json, &format!("start coto is missing: {start_coto}"));
                continue;
            }
        }
        if !ds.contains_coto(&conn_json.end)? {
            context.reject_connection(
                &conn_json,
                &format!("end coto is missing: {}", conn_json.end),
            );
            continue;
        }
        let ito = conn_json.into_ito(context.local_node_id, context.root_cotonoma.coto_id)?;
        let _ = ds.import_ito(&ito)?;
        context.on_connection_imported();
    }
    Ok(())
}

fn from_timestamp_millis(millis: i64) -> Result<NaiveDateTime> {
    DateTime::from_timestamp_millis(millis)
        .ok_or(anyhow!("The timestamp is out of range: {millis}"))
        .map(|dt| dt.naive_utc())
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

    inserted_at: i64, // epoch milliseconds
    updated_at: i64,  // epoch milliseconds
}

impl CotoJson {
    fn into_coto(self, node_id: Id<Node>) -> Result<Coto> {
        let (summary, content) = if self.as_cotonoma {
            // The original version uses the `content` field as a cotonoma name
            // while the new version uses the `summary` field.
            (self.content, None)
        } else {
            (self.summary, self.content)
        };
        Ok(Coto {
            uuid: self.id,
            rowid: 0,
            node_id,
            posted_in_id: self.posted_in_id,
            posted_by_id: node_id,
            content,
            media_content: None,
            media_type: None,
            summary,
            is_cotonoma: self.as_cotonoma,
            longitude: None,
            latitude: None,
            datetime_start: None,
            datetime_end: None,
            repost_of_id: self.repost_id,
            reposted_in_ids: None, // will be restored during inserts
            created_at: from_timestamp_millis(self.inserted_at)?,
            updated_at: from_timestamp_millis(self.updated_at)?,
        })
    }

    fn name_or_id(&self) -> String {
        self.cotonoma
            .as_ref()
            .map(|c| c.name.clone())
            .unwrap_or(self.id.to_string())
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
    /// The `start` node could be a coto or an amishi.
    ///
    /// If the `start` is an amishi, this connection is one of the "root connections" of the
    /// entire amishi's graph and which will be translated as an ito from root cotonoma
    /// during import.
    start: Uuid,
    end: Id<Coto>,

    created_by: Uuid, // amishi_id
    created_in: Option<Id<Cotonoma>>,

    linking_phrase: Option<String>,

    order: i32,

    created_at: i64, // epoch milliseconds
}

impl ConnectionJson {
    /// Returns true if this connection is one of the root connections of the entire amishi's graph.
    ///
    /// Because only an amishi themself can create a connection from their amishi node,
    /// it should be a root connection if `start` and `created_by` are the same value.
    fn is_root(&self) -> bool { self.start == self.created_by }

    fn start_as_coto(&self) -> Option<Id<Coto>> {
        if self.is_root() {
            None
        } else {
            Some(Id::new(self.start))
        }
    }

    fn into_ito(self, node_id: Id<Node>, root_coto_id: Id<Coto>) -> Result<Ito> {
        let source_coto_id = if let Some(start_coto_id) = self.start_as_coto() {
            start_coto_id
        } else {
            root_coto_id
        };
        Ok(Ito {
            uuid: Id::generate(),
            node_id,
            created_by_id: node_id,
            source_coto_id,
            target_coto_id: self.end,
            description: self.linking_phrase,
            details: None,
            order: self.order,
            created_at: from_timestamp_millis(self.created_at)?,
            updated_at: from_timestamp_millis(self.created_at)?,
        })
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
        let root_coto_id: Id<Coto> = Id::from_str("00000000-0000-0000-0000-000000000002")?;
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
        let ito = conn.into_ito(node_id, root_coto_id)?;

        assert_eq!(
            ito.source_coto_id,
            Id::from_str("f05c0f03-8bb0-430e-a4d2-714c2922e0cd")?
        );
        assert_eq!(
            ito.target_coto_id,
            Id::from_str("72972fe8-695c-4086-86ff-29a12c8a98a4")?
        );
        assert_eq!(ito.order, 1);
        assert_eq!(ito.description, None);

        Ok(())
    }

    #[test]
    fn deserialize_connection_json2() -> Result<()> {
        let node_id: Id<Node> = Id::from_str("00000000-0000-0000-0000-000000000001")?;
        let root_coto_id: Id<Coto> = Id::from_str("00000000-0000-0000-0000-000000000002")?;
        let json = indoc! {r#"
            {
                "start": "55111bd3-92e2-4b02-bc1a-15b74a945fd0",
                "order": 8,
                "end": "d1b71c83-9eca-41c2-96ae-ee63bc31696c",
                "created_by": "55111bd3-92e2-4b02-bc1a-15b74a945fd0",
                "created_at": 1576546971349
            }
        "#};
        let conn: ConnectionJson = serde_json::from_str(json)?;
        let ito = conn.into_ito(node_id, root_coto_id)?;

        assert_eq!(
            ito.source_coto_id,
            Id::from_str("00000000-0000-0000-0000-000000000002")?,
            "The source coto should be the root coto."
        );

        Ok(())
    }
}
