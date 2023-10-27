//! Database operations and transactions

use core::time::Duration;
use std::{
    collections::HashMap,
    path::{Path, PathBuf},
};

use anyhow::{anyhow, bail, Context as _, Result};
use diesel::{sqlite::SqliteConnection, Connection};
use diesel_migrations::{embed_migrations, EmbeddedMigrations, MigrationHarness};
use once_cell::unsync::OnceCell;
use parking_lot::{
    MappedRwLockReadGuard, MappedRwLockWriteGuard, Mutex, MutexGuard, RwLock, RwLockReadGuard,
    RwLockWriteGuard,
};
use tracing::{debug, info};
use url::Url;

use self::{
    error::*,
    op::{Context, Operation, WritableConn},
    operator::Operator,
    ops::*,
};
use crate::models::{
    changelog::{Change, ChangelogEntry},
    coto::{Coto, NewCoto},
    cotonoma::Cotonoma,
    link::{Link, NewLink},
    node::{
        child::{ChildNode, NewChildNode},
        local::LocalNode,
        parent::{NewParentNode, ParentNode},
        BelongsToNode, Node, Principal,
    },
    Id,
};

pub mod error;
pub mod op;
pub mod operator;
pub mod ops;
pub mod sqlite;

/////////////////////////////////////////////////////////////////////////////
// Database
/////////////////////////////////////////////////////////////////////////////

/// A Cotoami database instance based on SQLite
pub struct Database {
    /// The root directory of this database
    root_dir: PathBuf,

    /// Database file URI
    ///
    /// This URI should follow the spec described in the SQLite documentation.
    /// - <https://www.sqlite.org/uri.html>
    /// - <https://www.sqlite.org/c3ref/open.html>
    file_uri: String,

    /// A SqliteConnection for both read and write operations
    ///
    /// To avoid handling possible SQLITE_BUSY on concurrent writes,
    /// it keeps hold of a single read-write connection in the process.
    rw_conn: Mutex<WritableConn>,

    /// Globally shared information
    globals: Globals,
}

impl Database {
    const DATABASE_FILE_NAME: &'static str = "cotoami.db";
    const SQLITE_BUSY_TIMEOUT: Duration = Duration::from_millis(10_000);
    const MIGRATIONS: EmbeddedMigrations = embed_migrations!("migrations");

    pub fn new<P: AsRef<Path>>(root_dir: P) -> Result<Self> {
        let root_dir = root_dir.as_ref().canonicalize()?;
        if !root_dir.is_dir() {
            return Err(DatabaseError::InvalidRootDir(root_dir))?;
        }

        let file_uri = Self::to_file_uri(root_dir.join(Self::DATABASE_FILE_NAME))?;
        let rw_conn = Self::new_rw_conn(&file_uri)?;

        let db = Self {
            root_dir,
            file_uri,
            rw_conn: Mutex::new(rw_conn),
            globals: Globals::default(),
        };
        db.run_migrations()?;
        db.load_globals()?;

        info!("Database launched:");
        info!("  root_dir: {}", db.root_dir.display());
        info!("  file_uri: {}", db.file_uri);
        info!("  globals: {:?}", db.globals);

        Ok(db)
    }

    pub fn new_session(&self) -> Result<DatabaseSession<'_>> {
        Ok(DatabaseSession {
            globals: &self.globals,
            ro_conn: OnceCell::new(),
            new_ro_conn: Box::new(|| self.new_ro_conn()),
            rw_conn: Box::new(|| self.rw_conn.lock()),
        })
    }

    pub fn new_rw_conn(uri: &str) -> Result<WritableConn> {
        let mut rw_conn = SqliteConnection::establish(uri)?;
        sqlite::enable_foreign_key_constraints(&mut rw_conn)?;
        sqlite::enable_wal(&mut rw_conn)?;
        sqlite::set_busy_timeout(&mut rw_conn, Self::SQLITE_BUSY_TIMEOUT)?;
        Ok(WritableConn::new(rw_conn))
    }

    pub fn to_file_uri<P: AsRef<Path>>(path: P) -> Result<String> {
        let path = path.as_ref();
        if path.is_dir() {
            Err(DatabaseError::InvalidFilePath {
                path: path.to_path_buf(),
                reason: "The path is a directory".into(),
            })?
        } else {
            // Url::from_file_path
            // This returns Err if the given path is not absolute or, on Windows,
            // if the prefix is not a disk prefix (e.g. C:) or a UNC prefix (\\).
            // https://docs.rs/url/2.2.2/url/struct.Url.html#method.from_file_path
            let uri = Url::from_file_path(path).or(Err(DatabaseError::InvalidFilePath {
                path: path.to_path_buf(),
                reason: "Invalid path".into(),
            }))?;
            Ok(uri.as_str().to_owned())
        }
    }

    fn run_migrations(&self) -> Result<()> {
        let mut conn = SqliteConnection::establish(&self.file_uri)?;
        conn.run_pending_migrations(Self::MIGRATIONS).unwrap();
        Ok(())
    }

    fn new_ro_conn(&self) -> Result<SqliteConnection> {
        let database_uri = format!("{}?mode=ro&_txlock=deferred", &self.file_uri);
        let ro_conn = SqliteConnection::establish(&database_uri)?;
        Ok(ro_conn)
    }

    fn load_globals(&self) -> Result<()> {
        let mut db = self.new_session()?;
        let local_node = op::run_read(db.ro_conn()?, local_node_ops::get_pair())?;
        if let Some((ext, node)) = local_node {
            *self.globals.local_node_ext.write() = Some(ext);
            *self.globals.root_cotonoma_id.write() = node.root_cotonoma_id;
        }
        *self.globals.parent_node_exts.write() =
            op::run_read(db.ro_conn()?, parent_node_ops::all())?
                .into_iter()
                .map(|p| (p.node_id, p))
                .collect::<HashMap<_, _>>();

        Ok(())
    }
}

/////////////////////////////////////////////////////////////////////////////
// Globals
/////////////////////////////////////////////////////////////////////////////

/// Global information shared among sessions in a database.
/// Most of the fields are cached database rows or column values frequently used internally.
/// For example, [LocalNode] will be used every time when authentication is needed.
#[derive(Debug, Default)]
struct Globals {
    local_node_ext: RwLock<Option<LocalNode>>,
    root_cotonoma_id: RwLock<Option<Id<Cotonoma>>>,
    parent_node_exts: RwLock<HashMap<Id<Node>, ParentNode>>,
}

impl Globals {
    pub fn clear_parent_passwords(&self) {
        for parent in self.parent_node_exts.write().values_mut() {
            parent.encrypted_password = None;
        }
    }
}

/////////////////////////////////////////////////////////////////////////////
// DatabaseSession
/////////////////////////////////////////////////////////////////////////////

pub struct DatabaseSession<'a> {
    globals: &'a Globals,
    ro_conn: OnceCell<SqliteConnection>,
    new_ro_conn: Box<dyn Fn() -> Result<SqliteConnection> + 'a>,
    rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConn> + 'a>,
}

impl<'a> DatabaseSession<'a> {
    /////////////////////////////////////////////////////////////////////////////
    // local node
    /////////////////////////////////////////////////////////////////////////////

    /// Creates initial data that represents a local node and its root cotonoma.
    ///
    /// Majority of the database operations require this operation to be called in advance
    /// because every database entity belongs to a node.
    ///
    /// A root cotonoma will be created only if some `name` is specified,
    /// otherwise the local node will be initialized as cotonoma-less and its name
    /// will be set to empty string.
    pub fn init_as_node(
        &self,
        name: Option<&str>,
        password: Option<&str>,
    ) -> Result<((LocalNode, Node), ChangelogEntry)> {
        let result = self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let (ext, mut node) =
                local_node_ops::create(name.unwrap_or_default(), password).run(ctx)?;

            // Create a root cotonoma if the `name` is not None
            let change = if let Some(name) = name {
                let (node_updated, cotonoma, coto) =
                    node_ops::create_root_cotonoma(&node.uuid, name).run(ctx)?;
                node = node_updated;
                Change::CreateNode(node, Some((cotonoma, coto)))
            } else {
                Change::CreateNode(node, None)
            };

            let changelog = changelog_ops::log_change(&change, &ext.node_id).run(ctx)?;

            // Take the node data back from the `change` struct
            let Change::CreateNode(node, _) = change else { unreachable!() };

            Ok(((ext, node), changelog))
        });

        // Put the local node data in the global cache
        if let Ok(((ext, node), _)) = &result {
            *self.globals.local_node_ext.write() = Some(ext.clone());
            *self.globals.root_cotonoma_id.write() = node.root_cotonoma_id;
        }

        result
    }

    pub fn has_local_node_initialized(&self) -> bool {
        self.globals.local_node_ext.read().is_some()
    }

    pub fn local_node(&mut self) -> Result<Node> {
        if let Some((_, node)) = self.read_transaction(local_node_ops::get_pair())? {
            Ok(node)
        } else {
            bail!(DatabaseError::LocalNodeNotYetInitialized)
        }
    }

    pub fn local_node_pair(&mut self, operator: &Operator) -> Result<(LocalNode, Node)> {
        operator.requires_to_be_owner()?;
        let pair = self
            .read_transaction(local_node_ops::get_pair())?
            // Any operator doesn't exist without the local node initialized
            .unwrap_or_else(|| unreachable!());
        Ok(pair)
    }

    pub fn local_node_id(&self) -> Result<Id<Node>> { Ok(self.read_local_node_ext()?.node_id) }

    pub fn is_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> bool {
        self.ensure_local(entity).is_ok()
    }

    pub fn rename_local_node(
        &self,
        name: &str,
        operator: &Operator,
    ) -> Result<(Node, ChangelogEntry)> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let updated_at = crate::current_datetime();
            let node = node_ops::rename(&local_node_id, name, Some(updated_at)).run(ctx)?;
            let change = Change::RenameNode {
                uuid: local_node_id,
                name: name.into(),
                updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((node, changelog))
        })
    }

    pub fn set_root_cotonoma(
        &self,
        cotonoma_id: &Id<Cotonoma>,
        operator: &Operator,
    ) -> Result<(Node, ChangelogEntry)> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let node = node_ops::set_root_cotonoma(&local_node_id, cotonoma_id).run(ctx)?;
            let change = Change::SetRootCotonoma {
                uuid: local_node_id,
                cotonoma_id: *cotonoma_id,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((node, changelog))
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn node(&mut self, node_id: &Id<Node>) -> Result<Option<Node>> {
        self.read_transaction(node_ops::get(node_id))
    }

    pub fn all_nodes(&mut self) -> Result<Vec<Node>> { self.read_transaction(node_ops::all()) }

    /// Import a node data sent from a child or parent node.
    pub fn import_node(&self, node: &Node) -> Result<Option<(Node, ChangelogEntry)>> {
        let local_node_id = self.local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            if let Some(node) = node_ops::upsert(node).run(ctx)? {
                let change = Change::UpsertNode(node);
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                let Change::UpsertNode(node) = change else { unreachable!() };
                Ok(Some((node, changelog)))
            } else {
                Ok(None)
            }
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // node owner
    /////////////////////////////////////////////////////////////////////////////

    pub fn start_owner_session(&self, password: &str, duration: Duration) -> Result<LocalNode> {
        let mut local_node = self.write_local_node_ext()?;
        let duration = chrono::Duration::from_std(duration)?;
        local_node
            .start_session(password, duration)
            .context(DatabaseError::AuthenticationFailed)?;
        self.write_transaction(local_node_ops::update(&local_node))?;
        Ok(local_node.clone())
    }

    pub fn clear_owner_session(&self) -> Result<()> {
        let mut local_node = self.write_local_node_ext()?;
        local_node.clear_session();
        self.write_transaction(local_node_ops::update(&local_node))?;
        Ok(())
    }

    pub fn change_owner_password(&self, password: &str) -> Result<()> {
        let mut local_node = self.write_local_node_ext()?;
        local_node.update_password(password)?;
        self.write_transaction(local_node_ops::update(&local_node))?;
        self.clear_parent_passwords()?;
        Ok(())
    }

    /////////////////////////////////////////////////////////////////////////////
    // parent nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn all_parent_nodes(&mut self, operator: &Operator) -> Result<Vec<(ParentNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(parent_node_ops::all_pairs())
    }

    /// Registers the specified node as a parent.
    ///
    /// * The [Node] data has to be imported before registered as a [ParentNode].
    /// * A unique constraint error will be returned if the specified node has already been
    ///   registered as a parent.
    pub fn register_parent_node(
        &self,
        id: &Id<Node>,
        url_prefix: &str,
        operator: &Operator,
    ) -> Result<ParentNode> {
        operator.requires_to_be_owner()?;
        let new_parent_node = NewParentNode::new(id, url_prefix)?;
        let parent_node = self.write_transaction(parent_node_ops::insert(&new_parent_node))?;
        self.globals
            .parent_node_exts
            .write()
            .insert(*id, parent_node.clone());
        Ok(parent_node)
    }

    pub fn parent_node_ext(&mut self, id: &Id<Node>, operator: &Operator) -> Result<ParentNode> {
        operator.requires_to_be_owner()?;
        self.read_parent_node_ext(id).map(|ext| ext.clone())
    }

    pub fn save_parent_password(
        &self,
        id: &Id<Node>,
        password: &str,
        encryption_password: &str,
        operator: &Operator,
    ) -> Result<ParentNode> {
        operator.requires_to_be_owner()?;
        let mut parent_node = self.write_parent_node_ext(id)?;
        parent_node.save_password(password, encryption_password)?;
        self.write_transaction(parent_node_ops::update(&parent_node))
    }

    pub fn clear_parent_passwords(&self) -> Result<()> {
        self.write_transaction(parent_node_ops::clear_all_passwords())?;
        self.globals.clear_parent_passwords();
        Ok(())
    }

    pub fn set_parent_disabled(
        &self,
        id: &Id<Node>,
        disabled: bool,
        operator: &Operator,
    ) -> Result<ParentNode> {
        operator.requires_to_be_owner()?;

        let mut parent_node = self.write_parent_node_ext(id)?;

        // A forked parent can't be enabled
        if !disabled && parent_node.forked {
            bail!(DatabaseError::AlreadyForkedFromParent {
                parent_node_id: parent_node.node_id
            });
        }

        parent_node.disabled = disabled;
        self.write_transaction(parent_node_ops::update(&parent_node))
    }

    /// In Cotoami, `fork` means disconnecting from a parent node and taking the ownership of
    /// entities (cotos/cotonomas/links) owned by the parent until then. It also means that
    /// update requests to those entities won't be relayed to the parent anymore, instead
    /// the local node will handle them.
    ///
    /// Note that target entities by this function are only those that are owned by the specified
    /// parent, not by nodes indirectly connected via the parent. So if the parent has other nodes
    /// as its parent (grand parents), the local node won't receive the changes from those nodes anymore.
    ///
    /// This method is assumed to be used in the following situations:
    /// * Promoting a replica node to a primary/master node.
    /// * When there's a need for an intermediate node to take over the role of its parent node
    ///   because of the outage/retirement of the parent.
    pub fn fork_from(
        &self,
        parent_node_id: &Id<Node>,
        operator: &Operator,
    ) -> Result<(usize, ChangelogEntry)> {
        operator.requires_to_be_owner()?;
        let local_node_id = self.local_node_id()?;
        let mut parent_node = self.write_parent_node_ext(parent_node_id)?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            // Ensure to get the latest parent node data in the transaction
            *parent_node = parent_node_ops::get_or_err(parent_node_id).run(ctx)??;

            // Disable the parent not to be connected anymore
            parent_node.disabled = true;
            parent_node.forked = true;
            parent_node_ops::update(&parent_node).run(ctx)?;

            // Change the owner of every entity created in the parent node to the local node
            let affected = graph_ops::change_owner_node(parent_node_id, &local_node_id).run(ctx)?;

            let change = Change::ChangeOwnerNode {
                from: *parent_node_id,
                to: local_node_id,
                last_change_number: parent_node.changes_received,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;

            Ok((affected, changelog))
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // child nodes
    /////////////////////////////////////////////////////////////////////////////

    pub fn all_child_nodes(&mut self, operator: &Operator) -> Result<Vec<(ChildNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(child_node_ops::all_pairs())
    }

    pub fn recent_child_nodes(
        &mut self,
        page_size: i64,
        page_index: i64,
        operator: &Operator,
    ) -> Result<Paginated<(ChildNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(child_node_ops::recent_pairs(page_size, page_index))
    }

    /// Registers the specified node as a child.
    ///
    /// This operation is assumed to be invoked by a node owner to allow another node
    /// to connect to this node.
    ///
    /// If the node specified by the ID doesn't exist in this database (normally it doesn't),
    /// this function will create a placeholder row in the `nodes` table. The row will be
    /// updated with real data when the child node connects to this node.
    pub fn register_child_node(
        &self,
        id: Id<Node>,
        password: &str,
        as_owner: bool,
        can_edit_links: bool,
        operator: &Operator,
    ) -> Result<ChildNode> {
        operator.requires_to_be_owner()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let node = node_ops::get_or_insert_placeholder(id).run(ctx)?;
            let new_child = NewChildNode::new(&node.uuid, password, as_owner, can_edit_links)?;
            child_node_ops::insert(&new_child).run(ctx)
        })
    }

    pub fn start_child_session(
        &self,
        id: &Id<Node>,
        password: &str,
        duration: Duration,
    ) -> Result<ChildNode> {
        let duration = chrono::Duration::from_std(duration)?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let mut child_node = child_node_ops::get_or_err(id)
                .run(ctx)?
                // Hide a not-found error for a security reason
                .context(DatabaseError::AuthenticationFailed)?;
            child_node
                .start_session(password, duration)
                .context(DatabaseError::AuthenticationFailed)?;
            child_node = child_node_ops::update(&child_node).run(ctx)?;
            Ok(child_node)
        })
    }

    pub fn clear_child_session(&self, id: &Id<Node>) -> Result<()> {
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let mut child_node = child_node_ops::get_or_err(id).run(ctx)??;
            child_node.clear_session();
            child_node_ops::update(&child_node).run(ctx)?;
            Ok(())
        })
    }

    pub fn change_child_password(&self, id: &Id<Node>, password: &str) -> Result<()> {
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let mut child_node = child_node_ops::get_or_err(id).run(ctx)??;
            child_node.update_password(password)?;
            child_node_ops::update(&child_node).run(ctx)?;
            Ok(())
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // operator
    /////////////////////////////////////////////////////////////////////////////

    pub fn local_node_as_operator(&self) -> Result<Operator> {
        let local_node_id = self.local_node_id()?;
        Ok(Operator::Owner(local_node_id))
    }

    pub fn operator_in_session(&mut self, token: &str) -> Result<Option<Operator>> {
        // one of child nodes?
        if let Some(child_node) =
            self.read_transaction(child_node_ops::get_by_session_token(token))?
        {
            match child_node.verify_session(token) {
                Ok(_) => return Ok(Some(Operator::ChildNode(child_node))),
                Err(e) => debug!("ChildNode: {}", e),
            }
        }

        // the owner of local node?
        let local_node = self.read_local_node_ext()?;
        match local_node.verify_session(token) {
            Ok(_) => return Ok(Some(Operator::Owner(local_node.node_id))),
            Err(e) => debug!("Owner: {}", e),
        }

        Ok(None) // no session
    }

    /////////////////////////////////////////////////////////////////////////////
    // changelog
    /////////////////////////////////////////////////////////////////////////////

    pub fn import_change(
        &self,
        log: &ChangelogEntry,
        parent_node_id: &Id<Node>,
    ) -> Result<Option<ChangelogEntry>> {
        let mut parent_node = self.write_parent_node_ext(parent_node_id)?;
        self.write_transaction(changelog_ops::import_change(log, &mut parent_node))
    }

    pub fn chunk_of_changes(
        &mut self,
        from: i64,
        limit: i64,
    ) -> Result<(Vec<ChangelogEntry>, i64)> {
        self.read_transaction(changelog_ops::chunk(from, limit))
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotos
    /////////////////////////////////////////////////////////////////////////////

    pub fn coto(&mut self, id: &Id<Coto>) -> Result<Option<Coto>> {
        self.read_transaction(coto_ops::get(id))
    }

    pub fn all_cotos(&mut self) -> Result<Vec<Coto>> { self.read_transaction(coto_ops::all()) }

    pub fn recent_cotos(
        &mut self,
        node_id: Option<&Id<Node>>,
        posted_in_id: Option<&Id<Cotonoma>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Coto>> {
        self.read_transaction(coto_ops::recent(
            node_id,
            posted_in_id,
            page_size,
            page_index,
        ))
    }

    /// Posts a coto in the specified cotonoma.
    ///
    /// The target cotonoma has to belong to the local node,
    /// otherwise a change should be made via [Self::import_change()].
    pub fn post_coto(
        &self,
        content: &str,
        summary: Option<&str>,
        posted_in: &Cotonoma,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        self.ensure_local(posted_in)?;

        let local_node_id = self.local_node_id()?;
        let posted_by_id = operator.node_id();
        let new_coto = NewCoto::new(
            &local_node_id,
            &posted_in.uuid,
            &posted_by_id,
            content,
            summary,
        )?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let inserted_coto = coto_ops::insert(&new_coto).run(ctx)?;
            let change = Change::CreateCoto(inserted_coto.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((inserted_coto, changelog))
        })
    }

    pub fn edit_coto(
        &self,
        id: &Id<Coto>,
        content: &str,
        summary: Option<&str>,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        let local_node_id = self.local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::get_or_err(id).run(ctx)??;
            self.ensure_local(&coto)?;
            operator.can_update_coto(&coto)?;
            let coto = coto_ops::update(&coto.edit(content, summary)).run(ctx)?;
            let change = Change::EditCoto {
                uuid: *id,
                content: content.into(),
                summary: summary.map(String::from),
                updated_at: coto.updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((coto, changelog))
        })
    }

    pub fn delete_coto(&self, id: &Id<Coto>, operator: &Operator) -> Result<ChangelogEntry> {
        let local_node_id = self.local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::get_or_err(id).run(ctx)??;
            self.ensure_local(&coto)?;
            operator.can_delete_coto(&coto)?;
            if coto_ops::delete(id).run(ctx)? {
                let change = Change::DeleteCoto(*id);
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                Ok(changelog)
            } else {
                Err(DatabaseError::not_found(EntityKind::Coto, *id))?
            }
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotonomas
    /////////////////////////////////////////////////////////////////////////////

    pub fn root_cotonoma(&mut self) -> Result<Option<(Cotonoma, Coto)>> {
        if let Some(id) = self.globals.root_cotonoma_id.read().as_ref() {
            self.cotonoma(&id)
        } else {
            Ok(None)
        }
    }

    pub fn cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<Option<(Cotonoma, Coto)>> {
        self.read_transaction(cotonoma_ops::get(id))
    }

    pub fn cotonoma_or_err(&mut self, id: &Id<Cotonoma>) -> Result<(Cotonoma, Coto)> {
        let cotonoma = self.read_transaction(cotonoma_ops::get_or_err(id))??;
        Ok(cotonoma)
    }

    pub fn all_cotonomas(&mut self) -> Result<Vec<Cotonoma>> {
        self.read_transaction(cotonoma_ops::all())
    }

    pub fn recent_cotonomas(
        &mut self,
        node_id: Option<&Id<Node>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Cotonoma>> {
        self.read_transaction(cotonoma_ops::recent(node_id, page_size, page_index))
    }

    /////////////////////////////////////////////////////////////////////////////
    // links
    /////////////////////////////////////////////////////////////////////////////

    pub fn link(&mut self, link_id: &Id<Link>) -> Result<Option<Link>> {
        self.read_transaction(link_ops::get(link_id))
    }

    pub fn recent_links(
        &mut self,
        node_id: Option<&Id<Node>>,
        created_in_id: Option<&Id<Cotonoma>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Link>> {
        self.read_transaction(link_ops::recent(
            node_id,
            created_in_id,
            page_size,
            page_index,
        ))
    }

    pub fn create_link(
        &mut self,
        source_coto_id: &Id<Coto>,
        target_coto_id: &Id<Coto>,
        linking_phrase: Option<&str>,
        details: Option<&str>,
        created_in: Option<&Cotonoma>,
        operator: &Operator,
    ) -> Result<(Link, ChangelogEntry)> {
        operator.can_edit_links()?;

        if let Some(cotonoma) = created_in {
            self.ensure_local(cotonoma)?;
        }

        let local_node_id = self.local_node_id()?;
        let created_by_id = operator.node_id();
        let new_link = NewLink::new(
            &local_node_id,
            created_in.map(|c| &c.uuid),
            &created_by_id,
            source_coto_id,
            target_coto_id,
            linking_phrase,
            details,
        )?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let inserted_link = link_ops::insert(&new_link).run(ctx)?;
            let change = Change::CreateLink(inserted_link.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((inserted_link, changelog))
        })
    }

    /////////////////////////////////////////////////////////////////////////////
    // internals
    /////////////////////////////////////////////////////////////////////////////

    fn ro_conn(&mut self) -> Result<&mut SqliteConnection> {
        // https://github.com/matklad/once_cell/issues/194
        let _ = self.ro_conn.get_or_try_init(|| (self.new_ro_conn)())?;
        Ok(self.ro_conn.get_mut().unwrap_or_else(|| unreachable!()))
    }

    /// Runs a read operation in snapshot isolation.
    fn read_transaction<Op, T>(&mut self, op: Op) -> Result<T>
    where
        Op: Operation<SqliteConnection, T>,
    {
        op::run_read(self.ro_conn()?, op)
    }

    /// Runs a read/write operation.
    fn write_transaction<Op, T>(&self, op: Op) -> Result<T>
    where
        Op: Operation<WritableConn, T>,
    {
        op::run_write(&mut (self.rw_conn)(), op)
    }

    fn read_local_node_ext(&self) -> Result<MappedRwLockReadGuard<LocalNode>> {
        RwLockReadGuard::try_map(self.globals.local_node_ext.read(), |ext| ext.as_ref())
            .map_err(|_| anyhow!(DatabaseError::LocalNodeNotYetInitialized))
    }

    fn write_local_node_ext(&self) -> Result<MappedRwLockWriteGuard<LocalNode>> {
        RwLockWriteGuard::try_map(self.globals.local_node_ext.write(), |ext| ext.as_mut())
            .map_err(|_| anyhow!(DatabaseError::LocalNodeNotYetInitialized))
    }

    fn read_parent_node_ext(&self, id: &Id<Node>) -> Result<MappedRwLockReadGuard<ParentNode>> {
        RwLockReadGuard::try_map(self.globals.parent_node_exts.read(), |exts| exts.get(id))
            .map_err(|_| anyhow!(DatabaseError::not_found(EntityKind::ParentNode, *id)))
    }

    fn write_parent_node_ext(&self, id: &Id<Node>) -> Result<MappedRwLockWriteGuard<ParentNode>> {
        RwLockWriteGuard::try_map(self.globals.parent_node_exts.write(), |exts| {
            exts.get_mut(id)
        })
        .map_err(|_| anyhow!(DatabaseError::not_found(EntityKind::ParentNode, *id)))
    }

    fn ensure_local<T: BelongsToNode + std::fmt::Debug>(&self, entity: &T) -> Result<()> {
        let local_node_id = self.read_local_node_ext()?.node_id;
        if *entity.node_id() != local_node_id {
            bail!("The entity doesn't belong to the local node: {:?}", entity);
        }
        Ok(())
    }
}
