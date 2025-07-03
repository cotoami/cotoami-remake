use anyhow::{ensure, Result};

use crate::{
    db::{
        error::*,
        op::*,
        ops::{changelog_ops, ito_ops, Page},
        DatabaseSession,
    },
    models::prelude::*,
};

impl DatabaseSession<'_> {
    pub fn ito(&mut self, id: &Id<Ito>) -> Result<Option<Ito>> {
        self.read_transaction(ito_ops::get(id))
    }

    pub fn try_get_ito(&mut self, id: &Id<Ito>) -> Result<Ito> {
        self.read_transaction(ito_ops::try_get(id))?
            .map_err(anyhow::Error::from)
    }

    pub fn outgoing_itos(&mut self, coto_ids: &[Id<Coto>]) -> Result<Vec<Ito>> {
        self.read_transaction(ito_ops::outgoing(coto_ids))
    }

    pub fn sibling_itos(
        &mut self,
        source_coto_id: &Id<Coto>,
        node_id: Option<&Id<Node>>,
    ) -> Result<Vec<Ito>> {
        self.read_transaction(ito_ops::siblings(source_coto_id, node_id))
    }

    pub fn recent_itos(
        &mut self,
        node_id: Option<&Id<Node>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Page<Ito>> {
        self.read_transaction(ito_ops::recent(node_id, page_size, page_index))
    }

    pub fn determine_ito_node(
        &mut self,
        source: &Id<Coto>,
        target: &Id<Coto>,
        local_node_id: &Id<Node>,
    ) -> Result<Id<Node>> {
        self.read_transaction(ito_ops::determine_node(source, target, local_node_id))
    }

    /// Creates a new ito with [ItoInput] in the local node.
    ///
    /// Both of [ItoInput::source_coto_id] and [ItoInput::target_coto_id] must exist
    /// in the local node, otherwise a database constraint error will be returned.
    pub fn create_ito(
        &self,
        input: &ItoInput,
        operator: &Operator,
    ) -> Result<(Ito, ChangelogEntry)> {
        operator.can_edit_itos()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        let created_by_id = operator.try_get_node_id()?;
        let new_ito = NewIto::new(&local_node_id, &created_by_id, input)?;
        self.insert_ito(new_ito)
    }

    /// For import tools (ex. original Cotoami's JSON dumps).
    pub fn import_ito(&self, ito: &Ito) -> Result<(Ito, ChangelogEntry)> {
        self.globals.ensure_local(ito)?;
        self.insert_ito(ito.to_import())
    }

    /// Inserts a [NewIto] as a change originated in this node.
    /// Changes originated in remote nodes should be imported via [Self::import_change()].
    fn insert_ito(&self, new_ito: NewIto) -> Result<(Ito, ChangelogEntry)> {
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            ensure!(
                new_ito.node_id() == &local_node_id,
                "NewIto::node_id must be local."
            );

            let inserted_ito = ito_ops::insert(new_ito).run(ctx)?;
            let change = Change::CreateIto(inserted_ito.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((inserted_ito, changelog))
        })
    }

    pub fn edit_ito(
        &mut self,
        id: &Id<Ito>,
        diff: ItoContentDiff<'static>,
        operator: &Operator,
    ) -> Result<(Ito, ChangelogEntry)> {
        operator.can_edit_itos()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let ito = ito_ops::edit(id, &diff, None).run(ctx)?;
            self.globals.ensure_local(&ito)?;
            let change = Change::EditIto {
                ito_id: *id,
                diff,
                updated_at: ito.updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((ito, changelog))
        })
    }

    pub fn delete_ito(&self, id: &Id<Ito>, operator: &Operator) -> Result<ChangelogEntry> {
        operator.can_edit_itos()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let ito = ito_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&ito)?;
            if ito_ops::delete(id).run(ctx)? {
                let change = Change::DeleteIto { ito_id: *id };
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                Ok(changelog)
            } else {
                Err(DatabaseError::not_found(EntityKind::Ito, *id))?
            }
        })
    }

    pub fn change_ito_order(
        &mut self,
        id: &Id<Ito>,
        new_order: i32,
        operator: &Operator,
    ) -> Result<(Ito, ChangelogEntry)> {
        operator.can_edit_itos()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let ito = ito_ops::change_order(id, new_order).run(ctx)?;
            self.globals.ensure_local(&ito)?;
            let change = Change::ChangeItoOrder {
                ito_id: *id,
                new_order,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((ito, changelog))
        })
    }

    pub fn pin_parent_root(
        &mut self,
        parent_id: &Id<Node>,
    ) -> Result<Option<(Ito, Cotonoma, ChangelogEntry)>> {
        ensure!(
            self.globals.is_parent(parent_id),
            "The specified node is not a parent: {parent_id}"
        );

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

        // Create a ito between the two
        let (ito, change) = self.create_ito(
            &ItoInput::new(local_root_coto.uuid, parent_root_coto.uuid),
            &self.globals.local_node_as_operator()?,
        )?;
        Ok(Some((ito, parent_root_cotonoma, change)))
    }
}
