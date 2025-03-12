use std::collections::HashSet;

use anyhow::Result;
use diesel::sqlite::SqliteConnection;

use crate::{
    db::{
        error::DatabaseError,
        op::*,
        ops::{changelog_ops, coto_ops, cotonoma_ops, node_ops, Page},
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
    pub fn local_node_root(&mut self) -> Result<Option<(Cotonoma, Coto)>> {
        if let Some(id) = self.globals.root_cotonoma_id() {
            self.cotonoma_pair(&id)
        } else {
            Ok(None)
        }
    }

    pub fn try_get_local_node_root(&mut self) -> Result<(Cotonoma, Coto)> {
        self.local_node_root()?
            .ok_or(DatabaseError::RootCotonomaNotFound)
            .map_err(anyhow::Error::from)
    }

    pub fn all_node_roots(&mut self) -> Result<Vec<(Cotonoma, Coto)>> {
        self.read_transaction(|ctx: &mut Context<'_, SqliteConnection>| {
            let cotonoma_ids = node_ops::root_cotonoma_ids().run(ctx)?;
            cotonoma_ops::get_pairs_by_ids(&cotonoma_ids).run(ctx)
        })
    }

    pub fn cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<Option<Cotonoma>> {
        self.read_transaction(cotonoma_ops::get(id))
    }

    pub fn try_get_cotonoma(&mut self, id: &Id<Cotonoma>) -> Result<Cotonoma> {
        self.read_transaction(cotonoma_ops::try_get(id))?
            .map_err(anyhow::Error::from)
    }

    pub fn cotonoma_pair(&mut self, id: &Id<Cotonoma>) -> Result<Option<(Cotonoma, Coto)>> {
        self.read_transaction(cotonoma_ops::pair(id))
    }

    pub fn try_get_cotonoma_pair(&mut self, id: &Id<Cotonoma>) -> Result<(Cotonoma, Coto)> {
        self.read_transaction(cotonoma_ops::try_get_pair(id))?
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

    pub fn cotonomas(&mut self, ids: &[Id<Cotonoma>]) -> Result<Vec<Cotonoma>> {
        self.read_transaction(cotonoma_ops::get_by_ids(ids))
    }

    pub fn cotonomas_by_coto_ids(&mut self, ids: Vec<Id<Coto>>) -> Result<Vec<Cotonoma>> {
        self.read_transaction(cotonoma_ops::get_by_coto_ids(ids))
    }

    pub fn cotonomas_by_prefix(
        &mut self,
        prefix: &str,
        node_ids: Option<Vec<Id<Node>>>,
        limit: i64,
    ) -> Result<Vec<Cotonoma>> {
        self.read_transaction(cotonoma_ops::search_by_prefix(prefix, node_ids, limit))
    }

    pub fn all_cotonomas(&mut self) -> Result<Vec<Cotonoma>> {
        self.read_transaction(cotonoma_ops::all())
    }

    pub fn recent_cotonomas(
        &mut self,
        node_id: Option<&Id<Node>>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Page<Cotonoma>> {
        self.read_transaction(cotonoma_ops::recently_updated(
            node_id, page_size, page_index,
        ))
    }

    pub fn count_posts(&mut self, id: &Id<Cotonoma>) -> Result<i64> {
        self.read_transaction(cotonoma_ops::count_posts(id))
    }

    pub fn super_cotonomas(&mut self, coto: &Coto) -> Result<Vec<Cotonoma>> {
        let mut cotonoma_ids: Vec<Id<Cotonoma>> = Vec::new();
        if let Some(id) = coto.posted_in_id {
            cotonoma_ids.push(id);
        }
        if let Some(Ids(ref ids)) = coto.reposted_in_ids {
            cotonoma_ids.extend_from_slice(ids);
        }
        self.read_transaction(cotonoma_ops::get_by_ids(&cotonoma_ids))
    }

    pub fn sub_cotonomas(
        &mut self,
        id: &Id<Cotonoma>,
        page_size: i64,
        page_index: i64,
    ) -> Result<Page<Cotonoma>> {
        self.read_transaction(cotonoma_ops::subs(id, page_size, page_index))
    }

    pub fn cotonomas_of<'b, I>(&mut self, cotos: I) -> Result<Vec<Cotonoma>>
    where
        I: IntoIterator<Item = &'b Coto>,
    {
        let cotonoma_ids: HashSet<Id<Cotonoma>> = cotos
            .into_iter()
            .flat_map(|coto| {
                let mut ids = Vec::new();
                if let Some(posted_in_id) = coto.posted_in_id {
                    ids.push(posted_in_id);
                }
                if let Some(ref reposted_in_ids) = coto.reposted_in_ids {
                    ids.append(&mut reposted_in_ids.0.clone());
                }
                ids
            })
            .collect();
        let cotonoma_ids: Vec<Id<Cotonoma>> = cotonoma_ids.into_iter().collect();
        self.read_transaction(cotonoma_ops::get_by_ids(&cotonoma_ids))
    }

    pub fn as_cotonomas<'b, I>(&mut self, cotos: I) -> Result<Vec<Cotonoma>>
    where
        I: IntoIterator<Item = &'b Coto>,
    {
        let cotonoma_coto_ids: Vec<Id<Coto>> = cotos
            .into_iter()
            .filter_map(|coto| {
                if coto.is_cotonoma {
                    Some(coto.repost_of_id.unwrap_or(coto.uuid))
                } else {
                    None
                }
            })
            .collect();
        self.cotonomas_by_coto_ids(cotonoma_coto_ids)
    }

    pub fn post_cotonoma(
        &self,
        input: &CotonomaInput,
        posted_in: &Cotonoma,
        operator: &Operator,
    ) -> Result<((Cotonoma, Coto), ChangelogEntry)> {
        self.globals.ensure_local(posted_in)?;

        let local_node_id = self.globals.try_get_local_node_id()?;
        let posted_by_id = operator.try_get_node_id()?;
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let (cotonoma, coto) =
                cotonoma_ops::create(&local_node_id, &posted_in.uuid, &posted_by_id, input)
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
            let (coto, _) = coto_ops::insert(&coto.to_import()?).run(ctx)?;
            let cotonoma = cotonoma_ops::insert(&cotonoma.to_import()).run(ctx)?;
            let change = Change::CreateCotonoma(cotonoma.clone(), coto.clone());
            let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
            Ok(((cotonoma, coto), changelog))
        })
    }

    pub fn rename_cotonoma(
        &mut self,
        id: &Id<Cotonoma>,
        name: &str,
        operator: &Operator,
    ) -> Result<((Cotonoma, Coto), ChangelogEntry)> {
        if self.globals.is_root_cotonoma(id) {
            let (_, changelog) = self.rename_local_node(name, operator)?;
            let cotonoma_pair = self.try_get_local_node_root()?;
            Ok((cotonoma_pair, changelog))
        } else {
            let local_node_id = self.globals.try_get_local_node_id()?;
            self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
                let (cotonoma, coto) = cotonoma_ops::try_get_pair(id).run(ctx)??;
                self.globals.ensure_local(&cotonoma)?;
                operator.can_update_coto(&coto)?;

                let (cotonoma, coto) = cotonoma_ops::rename(id, name, None).run(ctx)?;
                let change = Change::RenameCotonoma {
                    cotonoma_id: *id,
                    name: name.into(),
                    updated_at: cotonoma.updated_at,
                };
                let changelog = changelog_ops::log_change(&change, &local_node_id).run(ctx)?;
                Ok(((cotonoma, coto), changelog))
            })
        }
    }
}
