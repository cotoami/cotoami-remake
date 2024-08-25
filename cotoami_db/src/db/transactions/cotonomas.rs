use std::collections::HashSet;

use anyhow::Result;

use crate::{
    db::{
        op::*,
        ops::{changelog_ops, coto_ops, cotonoma_ops, Paginated},
        DatabaseSession,
    },
    models::prelude::*,
};

impl<'a> DatabaseSession<'a> {
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
        self.read_transaction(cotonoma_ops::get_by_ids(cotonoma_ids.into_iter().collect()))
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
        name: &str,
        lng_lat: Option<(f64, f64)>,
        posted_in: &Cotonoma,
        operator: &Operator,
    ) -> Result<((Cotonoma, Coto), ChangelogEntry)> {
        self.globals.ensure_local(posted_in)?;

        let local_node_id = self.globals.try_get_local_node_id()?;
        let posted_by_id = operator.node_id();
        self.write_transaction(|ctx: &mut Context<'_, WritableConn>| {
            let (cotonoma, coto) = cotonoma_ops::create(
                &local_node_id,
                &posted_in.uuid,
                &posted_by_id,
                name,
                lng_lat,
            )
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
}
