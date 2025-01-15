use anyhow::{bail, Result};

use crate::{
    db::{
        error::*,
        op::*,
        ops::{changelog_ops, coto_ops, link_ops, Page},
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    pub fn link(&mut self, link_id: &Id<Link>) -> Result<Option<Link>> {
        self.read_transaction(link_ops::get(link_id))
    }

    pub fn links_by_source_coto_ids(&mut self, coto_ids: &[Id<Coto>]) -> Result<Vec<Link>> {
        self.read_transaction(link_ops::get_by_source_coto_ids(coto_ids))
    }

    pub fn recent_links(
        &mut self,
        node_id: Option<&Id<Node>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Page<Link>> {
        self.read_transaction(link_ops::recent(node_id, page_size, page_index))
    }

    pub fn connect<'b>(
        &self,
        input: &LinkInput,
        operator: &Operator,
    ) -> Result<(Link, ChangelogEntry)> {
        operator.can_edit_links()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        let created_by_id = operator.node_id();
        let new_link = NewLink::new(&local_node_id, &created_by_id, input)?;
        self.create_link(new_link)
    }

    pub fn import_link(&self, link: &Link) -> Result<(Link, ChangelogEntry)> {
        self.create_link(link.to_import())
    }

    /// Inserting a [NewLink] as a change originated in this node.
    /// Changes originated in remote nodes should be imported via [Self::import_change()].
    fn create_link(&self, new_link: NewLink) -> Result<(Link, ChangelogEntry)> {
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            // The source coto of the link must belong to the local node.
            let source_coto = coto_ops::try_get(new_link.source_coto_id()).run(ctx)??;
            self.globals.ensure_local(&source_coto)?;

            let inserted_link = link_ops::insert(new_link).run(ctx)?;
            let change = Change::CreateLink(inserted_link.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((inserted_link, changelog))
        })
    }

    pub fn edit_link(
        &mut self,
        id: &Id<Link>,
        diff: LinkContentDiff<'static>,
        operator: &Operator,
    ) -> Result<(Link, ChangelogEntry)> {
        operator.can_edit_links()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let link = link_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&link)?;
            let link = link_ops::edit(id, &diff, None).run(ctx)?;
            let change = Change::EditLink {
                link_id: *id,
                diff,
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
        let Some((_, local_root_coto)) = self.local_node_root()? else {
            return Ok(None);
        };

        // Parent root cotonoma
        let parent_node = self.try_get_node(parent_id)?;
        let (parent_root_cotonoma, parent_root_coto) =
            if let Some(parent_root_id) = parent_node.root_cotonoma_id {
                self.try_get_cotonoma_pair(&parent_root_id)?
            } else {
                return Ok(None);
            };

        // Create a link between the two
        let (link, change) = self.connect(
            &LinkInput::new(local_root_coto.uuid, parent_root_coto.uuid),
            &self.globals.local_node_as_operator()?,
        )?;
        Ok(Some((link, parent_root_cotonoma, change)))
    }
}
