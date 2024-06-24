use std::collections::HashSet;

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
    // cotonomas
    /////////////////////////////////////////////////////////////////////////////

    pub fn root_cotonoma(&mut self) -> Result<Option<(Cotonoma, Coto)>> {
        if let Some(id) = self.globals.root_cotonoma_id() {
            self.cotonoma(&id)
        } else {
            Ok(None)
        }
    }

    pub fn cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<Option<(Cotonoma, Coto)>> {
        self.read_transaction(cotonoma_ops::get(id))
    }

    pub fn try_get_cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<(Cotonoma, Coto)> {
        self.read_transaction(cotonoma_ops::try_get(id))?
            .map_err(anyhow::Error::from)
    }

    pub fn cotonoma_by_coto_id(&mut self, id: &Id<Coto>) -> Result<Option<(Cotonoma, Coto)>> {
        self.read_transaction(cotonoma_ops::get_by_coto_id(id))
    }

    pub fn try_get_cotonoma_by_coto_id(&mut self, id: &Id<Coto>) -> Result<(Cotonoma, Coto)> {
        self.read_transaction(cotonoma_ops::try_get_by_coto_id(id))?
            .map_err(anyhow::Error::from)
    }

    pub fn cotonoma_by_name(
        &mut self,
        name: &str,
        node_id: &Id<Node>,
    ) -> Result<Option<(Cotonoma, Coto)>> {
        self.read_transaction(cotonoma_ops::get_by_name(name, node_id))
    }

    pub fn try_get_cotonoma_by_name(
        &mut self,
        name: &str,
        node_id: &Id<Node>,
    ) -> Result<(Cotonoma, Coto)> {
        self.read_transaction(cotonoma_ops::try_get_by_name(name, node_id))?
            .map_err(anyhow::Error::from)
    }

    pub fn contains_cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<bool> {
        self.read_transaction(cotonoma_ops::contains(id))
    }

    pub fn cotonomas(&mut self, ids: Vec<Id<Cotonoma>>) -> Result<Vec<Cotonoma>> {
        self.read_transaction(cotonoma_ops::get_by_ids(ids))
    }

    pub fn cotonomas_by_coto_ids(&mut self, ids: Vec<Id<Coto>>) -> Result<Vec<Cotonoma>> {
        self.read_transaction(cotonoma_ops::get_by_coto_ids(ids))
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

    pub fn super_cotonomas(&mut self, coto: &Coto) -> Result<Vec<Cotonoma>> {
        let mut cotonoma_ids: Vec<Id<Cotonoma>> = Vec::new();
        if let Some(id) = coto.posted_in_id {
            cotonoma_ids.push(id);
        }
        if let Some(Ids(ref ids)) = coto.reposted_in_ids {
            cotonoma_ids.extend_from_slice(ids);
        }
        self.read_transaction(cotonoma_ops::get_by_ids(cotonoma_ids))
    }

    pub fn sub_cotonomas(
        &mut self,
        id: &Id<Cotonoma>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Cotonoma>> {
        self.read_transaction(cotonoma_ops::subs(id, page_size, page_index))
    }

    pub fn cotonomas_of<'b, I>(&mut self, cotos: I) -> Result<Vec<Cotonoma>>
    where
        I: IntoIterator<Item = &'b Coto>,
    {
        let cotonoma_ids: HashSet<Id<Cotonoma>> = cotos
            .into_iter()
            .map(|coto| {
                let mut ids = Vec::new();
                if let Some(posted_in_id) = coto.posted_in_id {
                    ids.push(posted_in_id);
                }
                if let Some(ref reposted_in_ids) = coto.reposted_in_ids {
                    ids.append(&mut reposted_in_ids.0.clone());
                }
                ids
            })
            .flatten()
            .collect();
        self.read_transaction(cotonoma_ops::get_by_ids(cotonoma_ids.into_iter().collect()))
    }

    pub fn as_cotonomas<'b, I>(&mut self, cotos: I) -> Result<Vec<Cotonoma>>
    where
        I: IntoIterator<Item = &'b Coto>,
    {
        let cotonoma_coto_ids: Vec<Id<Coto>> = cotos
            .into_iter()
            .map(|coto| {
                if coto.is_cotonoma {
                    Some(coto.repost_of_id.unwrap_or(coto.uuid))
                } else {
                    None
                }
            })
            .flatten()
            .collect();
        self.cotonomas_by_coto_ids(cotonoma_coto_ids)
    }

    pub fn post_cotonoma(
        &self,
        name: &str,
        posted_in: &Cotonoma,
        operator: &Operator,
    ) -> Result<((Cotonoma, Coto), ChangelogEntry)> {
        self.globals.ensure_local(posted_in)?;

        let local_node_id = self.globals.try_get_local_node_id()?;
        let posted_by_id = operator.node_id();
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let (cotonoma, coto) =
                cotonoma_ops::create(&local_node_id, &posted_in.uuid, &posted_by_id, name)
                    .run(ctx)?;
            let change = Change::CreateCotonoma(cotonoma.clone(), coto.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok(((cotonoma, coto), changelog))
        })
    }

    pub fn import_cotonoma(
        &self,
        coto: &Coto,
        cotonoma: &Cotonoma,
    ) -> Result<((Cotonoma, Coto), ChangelogEntry)> {
        assert_eq!(coto.uuid, cotonoma.coto_id);

        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::insert(&coto.to_import()).run(ctx)?;
            let cotonoma = cotonoma_ops::insert(&cotonoma.to_import()).run(ctx)?;
            let change = Change::CreateCotonoma(cotonoma.clone(), coto.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok(((cotonoma, coto), changelog))
        })
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
