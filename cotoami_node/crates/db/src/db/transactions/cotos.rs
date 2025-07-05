use std::collections::HashMap;

use anyhow::Result;

use crate::{
    db::{
        error::*,
        op::*,
        ops::{changelog_ops, coto_ops, cotonoma_ops, ito_ops, Page},
        DatabaseSession,
    },
    models::prelude::*,
};

impl DatabaseSession<'_> {
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

    pub fn cotos_map<'a>(
        &mut self,
        ids: impl IntoIterator<Item = &'a Id<Coto>>,
    ) -> Result<HashMap<Id<Coto>, Coto>> {
        self.read_transaction(coto_ops::map_from_ids(ids))
    }

    pub fn cotos(&mut self, ids: &[Id<Coto>]) -> Result<Vec<Coto>> {
        self.read_transaction(coto_ops::get_by_ids(ids))
    }

    pub fn all_cotos(&mut self) -> Result<Vec<Coto>> { self.read_transaction(coto_ops::all()) }

    pub fn recent_cotos(
        &mut self,
        node_id: Option<&Id<Node>>,
        posted_in_id: Option<&Id<Cotonoma>>,
        only_cotonomas: bool,
        page_size: i64,
        page_index: i64,
    ) -> Result<Page<Coto>> {
        self.read_transaction(coto_ops::recently_inserted(
            node_id,
            posted_in_id,
            only_cotonomas,
            page_size,
            page_index,
        ))
    }

    pub fn geolocated_cotos(
        &mut self,
        node_id: Option<&Id<Node>>,
        posted_in_id: Option<&Id<Cotonoma>>,
        limit: i64,
    ) -> Result<Vec<Coto>> {
        self.read_transaction(coto_ops::geolocated(node_id, posted_in_id, limit))
    }

    pub fn cotos_in_geo_bounds(
        &mut self,
        southwest: &Geolocation,
        northeast: &Geolocation,
        limit: i64,
    ) -> Result<Vec<Coto>> {
        self.read_transaction(coto_ops::in_geo_bounds(southwest, northeast, limit))
    }

    pub fn search_cotos(
        &mut self,
        query: &str,
        node_id: Option<&Id<Node>>,
        posted_in_id: Option<&Id<Cotonoma>>,
        only_cotonomas: bool,
        page_size: i64,
        page_index: i64,
    ) -> Result<Page<Coto>> {
        self.read_transaction(coto_ops::full_text_search(
            query,
            node_id,
            posted_in_id,
            only_cotonomas,
            page_size,
            page_index,
        ))
    }

    /// Posts a coto in the specified cotonoma.
    pub fn post_coto(
        &self,
        input: &CotoInput,
        post_to: &Id<Cotonoma>,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        operator.can_post_cotos()?;
        let local_node = self.globals.try_read_local_node()?;
        let posted_by_id = operator.try_get_node_id()?;
        let new_coto = NewCoto::new(
            &local_node.node_id,
            post_to,
            &posted_by_id,
            input,
            local_node.image_max_size(),
        )?;
        self.create_coto(&new_coto)
    }

    pub fn import_coto(&self, coto: &Coto) -> Result<(Coto, ChangelogEntry)> {
        let local_node = self.globals.try_read_local_node()?;
        self.create_coto(&coto.to_import(local_node.image_max_size())?)
    }

    /// Inserting a [NewCoto] as a change originated in this node.
    /// Changes originated in remote nodes should be imported via [Self::import_change()].
    fn create_coto(&self, new_coto: &NewCoto) -> Result<(Coto, ChangelogEntry)> {
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            // The target cotonoma must belong to the local node.
            if let Some(posted_in_id) = new_coto.posted_in_id() {
                let posted_in = cotonoma_ops::try_get(posted_in_id).run(ctx)??;
                self.globals.ensure_local(&posted_in)?;
            }

            let (inserted_coto, _) = coto_ops::insert(new_coto).run(ctx)?;
            let change = Change::CreateCoto(inserted_coto.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok((inserted_coto, changelog))
        })
    }

    pub fn edit_coto(
        &self,
        id: &Id<Coto>,
        diff: CotoContentDiff<'static>,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        let local_node = self.globals.try_read_local_node()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            // Permission check
            let coto = coto_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&coto)?;
            operator.can_update_coto(&coto)?;

            // Do edit
            let coto = coto_ops::edit(id, &diff, local_node.image_max_size(), None).run(ctx)?;

            // Log change
            let change = Change::EditCoto {
                coto_id: *id,
                diff,
                updated_at: coto.updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node.node_id).run(ctx)?;

            Ok((coto, changelog))
        })
    }

    pub fn promote(
        &self,
        coto_id: &Id<Coto>,
        operator: &Operator,
    ) -> Result<((Cotonoma, Coto), ChangelogEntry)> {
        operator.can_post_cotonomas()?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            // Permission check
            let coto = coto_ops::try_get(coto_id).run(ctx)??;
            self.globals.ensure_local(&coto)?;
            operator.can_update_coto(&coto)?;

            // Do promote
            let (cotonoma, coto) = coto_ops::promote(coto_id, None, None).run(ctx)?;

            // Log change
            let change = Change::PromoteCoto {
                coto_id: *coto_id,
                promoted_at: cotonoma.created_at,
                cotonoma_id: cotonoma.uuid,
            };
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;

            Ok(((cotonoma, coto), changelog))
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
                Err(DatabaseError::not_found(EntityKind::Coto, *id))?
            }
        })
    }

    pub fn repost(
        &self,
        id: &Id<Coto>,
        dest: &Cotonoma,
        operator: &Operator,
    ) -> Result<((Coto, Coto), ChangelogEntry)> {
        self.globals.ensure_local(dest)?;
        let local_node_id = self.globals.try_get_local_node_id()?;
        let reposted_by = operator.try_get_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let (repost, original) =
                coto_ops::repost(id, &dest.uuid, &reposted_by, None).run(ctx)?;
            let change = Change::CreateCoto(repost.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok(((repost, original), changelog))
        })
    }

    /// Posting a subcoto as a single atomic operation.
    ///
    /// TODO: Update [ChangelogEntry] to be able to have multiple changes so that
    /// the [Change]s can also be applied as a single atomic operation.
    pub fn post_subcoto(
        &self,
        source_coto_id: &Id<Coto>,
        coto_input: &CotoInput,
        post_to: &Id<Cotonoma>,
        operator: &Operator,
    ) -> Result<((Coto, Ito), Vec<ChangelogEntry>)> {
        operator.can_post_cotos()?;
        operator.can_edit_itos()?;
        let local_node = self.globals.try_read_local_node()?;
        let poster = operator.try_get_node_id()?;
        let new_coto = NewCoto::new(
            &local_node.node_id,
            post_to,
            &poster,
            coto_input,
            local_node.image_max_size(),
        )?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let post_to = cotonoma_ops::try_get(post_to).run(ctx)??;
            self.globals.ensure_local(&post_to)?;

            // Create a coto
            let (inserted_coto, _) = coto_ops::insert(&new_coto).run(ctx)?;
            let change = Change::CreateCoto(inserted_coto.clone());
            let changelog1 = changelog_ops::log_change(&change, &local_node.node_id).run(ctx)?;

            // Create an ito
            let ito_input = ItoInput::new(*source_coto_id, inserted_coto.uuid);
            let new_ito = NewIto::new(&local_node.node_id, &poster, &ito_input)?;
            let inserted_ito = ito_ops::insert(new_ito).run(ctx)?;
            let change = Change::CreateIto(inserted_ito.clone());
            let changelog2 = changelog_ops::log_change(&change, &local_node.node_id).run(ctx)?;

            Ok(((inserted_coto, inserted_ito), vec![changelog1, changelog2]))
        })
    }
}
