use anyhow::{bail, Result};
use diesel::sqlite::SqliteConnection;
use once_cell::unsync::OnceCell;
use parking_lot::MutexGuard;

use crate::{
    db::{
        error::*,
        op,
        op::{Context, Operation, WritableConn},
        ops::prelude::*,
        Globals,
    },
    models::prelude::*,
};

pub mod changes;
pub mod cotonomas;
pub mod cotos;
pub mod nodes;

pub struct DatabaseSession<'a> {
    globals: &'a Globals,
    ro_conn: OnceCell<SqliteConnection>,

    // The following fields were once defined as generic types. However,
    // it turned out to make it awkward to use the `DatabaseSession` type
    // because of the trait bounds, which unnecessarily expose
    // the internal concerns (`SqliteConnection` and `WritableConn`) to users of the type.
    new_ro_conn: Box<dyn Fn() -> Result<SqliteConnection> + 'a>,
    lock_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConn> + 'a>,
}

impl<'a> DatabaseSession<'a> {
    pub(super) fn new(
        globals: &'a Globals,
        new_ro_conn: Box<dyn Fn() -> Result<SqliteConnection> + 'a>,
        lock_rw_conn: Box<dyn Fn() -> MutexGuard<'a, WritableConn> + 'a>,
    ) -> Self {
        Self {
            globals,
            ro_conn: OnceCell::new(),
            new_ro_conn,
            lock_rw_conn,
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    // links
    /////////////////////////////////////////////////////////////////////////////

    pub fn link(&mut self, link_id: &Id<Link>) -> Result<Option<Link>> {
        self.read_transaction(link_ops::get(link_id))
    }

    pub fn links_by_source_coto_ids(&mut self, coto_ids: &Vec<Id<Coto>>) -> Result<Vec<Link>> {
        self.read_transaction(link_ops::get_by_source_coto_ids(coto_ids))
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

    pub fn connect(
        &self,
        source_coto_id: &Id<Coto>,
        target_coto_id: &Id<Coto>,
        linking_phrase: Option<&str>,
        details: Option<&str>,
        order: Option<i32>,
        created_in: Option<&Cotonoma>,
        operator: &Operator,
    ) -> Result<(Link, ChangelogEntry)> {
        operator.can_edit_links()?;

        if let Some(cotonoma) = created_in {
            self.globals.ensure_local(cotonoma)?;
        }

        let local_node_id = self.globals.try_get_local_node_id()?;
        let created_by_id = operator.node_id();
        let new_link = NewLink::new(
            &local_node_id,
            created_in.map(|c| &c.uuid),
            &created_by_id,
            source_coto_id,
            target_coto_id,
            linking_phrase,
            details,
            order,
        )?;
        self.create_link(new_link)
    }

    pub fn import_link(&self, link: &Link) -> Result<(Link, ChangelogEntry)> {
        self.create_link(link.to_import())
    }

    fn create_link(&self, new_link: NewLink) -> Result<(Link, ChangelogEntry)> {
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let inserted_link = link_ops::insert(new_link).run(ctx)?;
            let change = Change::CreateLink(inserted_link.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((inserted_link, changelog))
        })
    }

    pub fn edit_link(
        &mut self,
        id: &Id<Link>,
        linking_phrase: Option<&str>,
        details: Option<&str>,
        operator: &Operator,
    ) -> Result<(Link, ChangelogEntry)> {
        operator.can_edit_links()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let link = link_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&link)?;
            let link = link_ops::update(&link.edit(linking_phrase, details)).run(ctx)?;
            let change = Change::EditLink {
                link_id: *id,
                linking_phrase: linking_phrase.map(String::from),
                details: details.map(String::from),
                updated_at: link.updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((link, changelog))
        })
    }

    pub fn delete_link(&self, id: &Id<Link>, operator: &Operator) -> Result<ChangelogEntry> {
        operator.can_edit_links()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let link = link_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&link)?;
            if link_ops::delete(id).run(ctx)? {
                let change = Change::DeleteLink(*id);
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                Ok(changelog)
            } else {
                Err(DatabaseError::not_found(EntityKind::Link, "uuid", *id))?
            }
        })
    }

    pub fn pin_parent_root(
        &mut self,
        parent_id: &Id<Node>,
    ) -> Result<Option<(Link, Cotonoma, ChangelogEntry)>> {
        if !self.globals.is_parent(parent_id) {
            bail!("The specified node is not a parent: {parent_id}");
        }

        // Local root cotonoma
        let Some((_, local_root_coto)) = self.root_cotonoma()? else {
            return Ok(None);
        };

        // Parent root cotonoma
        let parent_node = self.try_get_node(parent_id)?;
        let (parent_root_cotonoma, parent_root_coto) =
            if let Some(parent_root_id) = parent_node.root_cotonoma_id {
                self.try_get_cotonoma(&parent_root_id)?
            } else {
                return Ok(None);
            };

        // Create a link between the two.
        let (link, change) = self.connect(
            &local_root_coto.uuid,
            &parent_root_coto.uuid,
            None,
            None,
            None,
            None,
            &self.globals.local_node_as_operator()?,
        )?;
        Ok(Some((link, parent_root_cotonoma, change)))
    }

    /////////////////////////////////////////////////////////////////////////////
    // graph
    /////////////////////////////////////////////////////////////////////////////

    pub fn graph(&mut self, root: Coto, until_cotonoma: bool) -> Result<Graph> {
        self.read_transaction(graph_ops::traverse_by_level_queries(root, until_cotonoma))
    }

    pub fn graph_by_cte(&mut self, root: Coto, until_cotonoma: bool) -> Result<Graph> {
        self.read_transaction(graph_ops::traverse_by_recursive_cte(root, until_cotonoma))
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
        op::run_write(&mut (self.lock_rw_conn)(), op)
    }
}
