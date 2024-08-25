use anyhow::Result;

use crate::{
    db::{
        error::*,
        op::*,
        ops::{changelog_ops, coto_ops, Paginated},
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
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
        media_content: Option<(&[u8], &str)>,
        lng_lat: Option<(f64, f64)>,
        posted_in: &Cotonoma,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        self.globals.ensure_local(posted_in)?;
        let local_node = self.globals.try_read_local_node()?;
        let posted_by_id = operator.node_id();
        let new_coto = NewCoto::new(
            &local_node.node_id,
            &posted_in.uuid,
            &posted_by_id,
            content,
            summary,
            media_content,
            local_node.image_max_size.map(|size| size as u32),
            lng_lat,
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
            let coto = coto_ops::edit(id, content, summary, None).run(ctx)?;
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

    pub fn set_media_content(
        &self,
        id: &Id<Coto>,
        media_content: Option<(&'a [u8], &'a str)>,
        operator: &Operator,
    ) -> Result<(Coto, ChangelogEntry)> {
        let local_node = self.globals.try_read_local_node()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let coto = coto_ops::try_get(id).run(ctx)??;
            self.globals.ensure_local(&coto)?;
            operator.can_update_coto(&coto)?;
            let coto = coto_ops::set_media_content(
                id,
                media_content,
                local_node.image_max_size.map(|size| size as u32),
                None,
            )
            .run(ctx)?;
            let change = Change::SetMediaContent {
                coto_id: *id,
                content: coto.media_content(),
                updated_at: coto.updated_at,
            };
            let changelog = changelog_ops::log_change(&change, &local_node.node_id).run(ctx)?;
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
}
