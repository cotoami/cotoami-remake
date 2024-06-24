use core::time::Duration;
use std::collections::{HashMap, HashSet};

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
    // network role
    /////////////////////////////////////////////////////////////////////////////

    pub fn set_network_disabled(
        &self,
        id: &Id<Node>,
        disabled: bool,
        operator: &Operator,
    ) -> Result<NetworkRole> {
        operator.requires_to_be_owner()?;
        self.write_transaction(node_role_ops::set_network_disabled(id, disabled))
    }

    /////////////////////////////////////////////////////////////////////////////
    // network role / server
    /////////////////////////////////////////////////////////////////////////////

    pub fn server_node(
        &mut self,
        id: &Id<Node>,
        operator: &Operator,
    ) -> Result<Option<ServerNode>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(server_ops::get(id))
    }

    pub fn all_server_nodes(&mut self, operator: &Operator) -> Result<Vec<(ServerNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(server_ops::all_pairs())
    }

    /// Registers the specified node as a server.
    ///
    /// * The [Node] data has to be imported before registered as a [ServerNode].
    /// * A unique constraint error will be returned if the specified node has already been
    ///   registered as a server.
    pub fn register_server_node(
        &self,
        id: &Id<Node>,
        url_prefix: &str,
        database_role: NewDatabaseRole,
        operator: &Operator,
    ) -> Result<(ServerNode, DatabaseRole)> {
        operator.requires_to_be_owner()?;
        let (server, database_role) = self.write_transaction(
            node_role_ops::register_server_node(id, url_prefix, database_role),
        )?;
        if let DatabaseRole::Parent(parent) = &database_role {
            self.globals.cache_parent_node(parent.clone());
        }
        Ok((server, database_role))
    }

    pub fn register_server_node_as_parent(
        &self,
        id: &Id<Node>,
        url_prefix: &str,
        operator: &Operator,
    ) -> Result<(ServerNode, ParentNode)> {
        let (server, database_role) =
            self.register_server_node(id, url_prefix, NewDatabaseRole::Parent, operator)?;
        let DatabaseRole::Parent(parent) = database_role else { unreachable!() };
        Ok((server, parent))
    }

    pub fn save_server_password(
        &self,
        id: &Id<Node>,
        password: &str,
        encryption_password: &str,
        operator: &Operator,
    ) -> Result<ServerNode> {
        operator.requires_to_be_owner()?;
        self.write_transaction(server_ops::save_server_password(
            id,
            password,
            encryption_password,
        ))
    }

    /////////////////////////////////////////////////////////////////////////////
    // network role / client
    /////////////////////////////////////////////////////////////////////////////

    pub fn all_client_nodes(&mut self, operator: &Operator) -> Result<Vec<(ClientNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(client_ops::all_pairs())
    }

    pub fn recent_client_nodes(
        &mut self,
        page_size: i64,
        page_index: i64,
        operator: &Operator,
    ) -> Result<Paginated<(ClientNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(client_ops::recent_pairs(page_size, page_index))
    }

    /// Registers the specified node as a client.
    ///
    /// This operation is assumed to be invoked by a node owner to allow another node
    /// to connect to this node.
    ///
    /// If the node specified by the ID doesn't exist in this database (normally it doesn't),
    /// this function will create a placeholder row in the `nodes` table. The row will be
    /// updated with real data when the client node connects to this node.
    pub fn register_client_node(
        &self,
        id: Id<Node>,
        password: &str,
        database_role: NewDatabaseRole,
        operator: &Operator,
    ) -> Result<(ClientNode, DatabaseRole)> {
        operator.requires_to_be_owner()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let node = node_ops::get_or_insert_placeholder(id).run(ctx)?;
            let (client, database_role) =
                node_role_ops::register_client_node(&node.uuid, password, database_role)
                    .run(ctx)?;
            if let DatabaseRole::Parent(parent) = &database_role {
                self.globals.cache_parent_node(parent.clone());
            }
            Ok((client, database_role))
        })
    }

    pub fn start_client_node_session(
        &self,
        id: &Id<Node>,
        password: &str,
        duration: Duration,
    ) -> Result<ClientNode> {
        self.write_transaction(client_ops::start_session(id, password, duration))
    }

    pub fn clear_client_node_session(&self, id: &Id<Node>) -> Result<ClientNode> {
        self.write_transaction(client_ops::clear_session(id))
    }

    pub fn change_client_node_password(&self, id: &Id<Node>, password: &str) -> Result<ClientNode> {
        self.write_transaction(client_ops::change_password(id, password))
    }

    pub fn client_session(&mut self, token: &str) -> Result<Option<ClientSession>> {
        // a client node?
        if let Some(client) = self.read_transaction(client_ops::get_by_session_token(token))? {
            if client.verify_session(token).is_ok() {
                match self.database_role_of(&client.node_id)? {
                    Some(DatabaseRole::Parent(parent)) => {
                        return Ok(Some(ClientSession::ParentNode(parent)));
                    }
                    Some(DatabaseRole::Child(child)) => {
                        return Ok(Some(ClientSession::Operator(Operator::ChildNode(child))));
                    }
                    None => (),
                }
            }
        }

        // the owner of local node?
        let local_node = self.globals.try_read_local_node()?;
        if local_node.verify_session(token).is_ok() {
            return Ok(Some(ClientSession::Operator(Operator::Owner(
                local_node.node_id,
            ))));
        }

        Ok(None) // no session
    }

    /////////////////////////////////////////////////////////////////////////////
    // database role / parent
    /////////////////////////////////////////////////////////////////////////////

    pub fn parent_node(&self, id: &Id<Node>, operator: &Operator) -> Result<Option<ParentNode>> {
        operator.requires_to_be_owner()?;
        Ok(self.globals.parent_node(id))
    }

    pub fn database_role_of(&mut self, node_id: &Id<Node>) -> Result<Option<DatabaseRole>> {
        self.read_transaction(node_role_ops::database_role_of(node_id))
    }

    pub fn database_roles_of(
        &mut self,
        node_ids: &Vec<Id<Node>>,
    ) -> Result<HashMap<Id<Node>, DatabaseRole>> {
        self.read_transaction(node_role_ops::database_roles_of(node_ids))
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
        let local_node_id = self.globals.try_get_local_node_id()?;
        let mut parent_node = self.globals.try_write_parent_node(parent_node_id)?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            // Set the parent to be forked
            *parent_node = node_role_ops::fork_from(parent_node_id).run(ctx)?;

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
    // database role / child
    /////////////////////////////////////////////////////////////////////////////

    pub fn recent_child_nodes(
        &mut self,
        page_size: i64,
        page_index: i64,
        operator: &Operator,
    ) -> Result<Paginated<(ChildNode, Node)>> {
        operator.requires_to_be_owner()?;
        self.read_transaction(child_ops::recent_pairs(page_size, page_index))
    }

    pub fn edit_child_node(
        &self,
        id: &Id<Node>,
        as_owner: bool,
        can_edit_links: bool,
    ) -> Result<ChildNode> {
        self.write_transaction(child_ops::edit(id, as_owner, can_edit_links))
    }

    /////////////////////////////////////////////////////////////////////////////
    // operator
    /////////////////////////////////////////////////////////////////////////////

    pub fn as_operator(&mut self, node_id: Id<Node>) -> Result<Option<Operator>> {
        if node_id == self.globals.try_get_local_node_id()? {
            return Ok(Some(Operator::Owner(node_id)));
        }
        if let Some(child) = self.read_transaction(child_ops::get(&node_id))? {
            return Ok(Some(Operator::ChildNode(child)));
        }
        Ok(None)
    }

    /////////////////////////////////////////////////////////////////////////////
    // changelog
    /////////////////////////////////////////////////////////////////////////////

    pub fn import_change(
        &self,
        log: &ChangelogEntry,
        parent_node_id: &Id<Node>,
    ) -> Result<Option<ChangelogEntry>> {
        let mut parent_node = self.globals.try_write_parent_node(parent_node_id)?;
        self.write_transaction(changelog_ops::import_change(log, &mut parent_node))
    }

    pub fn chunk_of_changes(
        &mut self,
        from: i64,
        limit: i64,
    ) -> Result<(Vec<ChangelogEntry>, i64)> {
        self.read_transaction(changelog_ops::chunk(from, limit))
    }

    pub fn last_change_number(&mut self) -> Result<Option<i64>> {
        self.read_transaction(changelog_ops::last_serial_number())
    }

    /////////////////////////////////////////////////////////////////////////////
    // cotos
    /////////////////////////////////////////////////////////////////////////////

    pub fn coto(&mut self, id: &Id<Coto>) -> Result<Option<Coto>> {
        self.read_transaction(coto_ops::get(id))
    }

    pub fn try_get_coto(&mut self, id: &Id<Coto>) -> Result<Coto> {
        self.read_transaction(coto_ops::try_get(id))?
            .map_err(anyhow::Error::from)
    }

    pub fn contains_coto(&mut self, id: &Id<Coto>) -> Result<bool> {
        self.read_transaction(coto_ops::contains(id))
    }

    pub fn cotos(&mut self, ids: Vec<Id<Coto>>) -> Result<Vec<Coto>> {
        self.read_transaction(coto_ops::get_by_ids(ids))
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

    pub fn search_cotos(
        &mut self,
        query: &str,
        node_id: Option<&Id<Node>>,
        posted_in_id: Option<&Id<Cotonoma>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Paginated<Coto>> {
        self.read_transaction(coto_ops::full_text_search(
            query,
            node_id,
            posted_in_id,
            page_size,
            page_index,
        ))
    }

    /// Posts a coto in the specified cotonoma.
    ///
    /// The target cotonoma (`posted_in`) has to belong to the local node,
    /// otherwise a change should be made via [Self::import_change()].
    pub fn post_coto(
        &self,
        content: &str,
        summary: Option<&str>,
        posted_in: &Cotonoma,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        self.globals.ensure_local(posted_in)?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        let posted_by_id = operator.node_id();
        let new_coto = NewCoto::new(
            &local_node_id,
            &posted_in.uuid,
            &posted_by_id,
            content,
            summary,
        )?;
        self.create_coto(&new_coto)
    }

    pub fn import_coto(&self, coto: &Coto) -> Result<(Coto, ChangelogEntry)> {
        self.create_coto(&coto.to_import())
    }

    fn create_coto(&self, new_coto: &NewCoto) -> Result<(Coto, ChangelogEntry)> {
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let inserted_coto = coto_ops::insert(new_coto).run(ctx)?;
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
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&coto)?;
            operator.can_update_coto(&coto)?;
            let coto = coto_ops::update(&coto.edit(content, summary)).run(ctx)?;
            let change = Change::EditCoto {
                coto_id: *id,
                content: content.into(),
                summary: summary.map(String::from),
                updated_at: coto.updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((coto, changelog))
        })
    }

    pub fn delete_coto(&self, id: &Id<Coto>, operator: &Operator) -> Result<ChangelogEntry> {
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&coto)?;
            operator.can_delete_coto(&coto)?;
            if let Some(deleted_at) = coto_ops::delete(id, None).run(ctx)? {
                let change = Change::DeleteCoto {
                    coto_id: *id,
                    deleted_at,
                };
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                Ok(changelog)
            } else {
                Err(DatabaseError::not_found(EntityKind::Coto, "uuid", *id))?
            }
        })
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
