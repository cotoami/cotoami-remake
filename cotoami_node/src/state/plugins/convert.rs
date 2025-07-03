use std::{borrow::Cow, str::FromStr};

use anyhow::Result;
use cotoami_db::prelude::*;
use cotoami_plugin_api::Event;

use crate::state::NodeState;

pub(crate) async fn into_plugin_event(
    change: Change,
    local_node_id: Id<Node>,
    node_state: NodeState,
) -> Option<Event> {
    let local_node_id = local_node_id.to_string();
    match change {
        Change::CreateCoto(coto) => into_plugin_coto(coto).map(|coto| Event::CotoPosted {
            coto,
            local_node_id,
        }),
        Change::EditCoto { coto_id, .. } => node_state
            .coto(coto_id)
            .await
            .ok()
            .and_then(into_plugin_coto)
            .map(|coto| Event::CotoUpdated {
                coto,
                local_node_id,
            }),
        _ => None,
    }
}

pub(crate) fn into_plugin_node(node: Node) -> cotoami_plugin_api::Node {
    cotoami_plugin_api::Node {
        uuid: node.uuid.to_string(),
        name: node.name,
    }
}

pub(crate) fn into_plugin_coto(coto: Coto) -> Option<cotoami_plugin_api::Coto> {
    coto.posted_in_id
        .map(|posted_in_id| cotoami_plugin_api::Coto {
            uuid: coto.uuid.to_string(),
            node_id: coto.node_id.to_string(),
            posted_in_id: posted_in_id.to_string(),
            posted_by_id: coto.posted_by_id.to_string(),
            content: coto.content,
            summary: coto.summary,
            media_content: match (coto.media_content, coto.media_type) {
                (Some(c), Some(t)) => Some((c.inner(), t)),
                _ => None,
            },
            is_cotonoma: coto.is_cotonoma,
            geolocation: match (coto.longitude, coto.latitude) {
                (Some(longitude), Some(latitude)) => Some(cotoami_plugin_api::Geolocation {
                    longitude,
                    latitude,
                }),
                _ => None,
            },
            datetime_start: coto.datetime_start.map(|time| time.to_string()),
            datetime_end: coto.datetime_end.map(|time| time.to_string()),
            repost_of_id: coto.repost_of_id.map(|id| id.to_string()),
            created_at: coto.created_at.to_string(),
            updated_at: coto.updated_at.to_string(),
        })
}

pub(crate) fn into_plugin_ito(ito: Ito) -> cotoami_plugin_api::Ito {
    cotoami_plugin_api::Ito {
        uuid: ito.uuid.to_string(),
        node_id: ito.node_id.to_string(),
        created_by_id: ito.created_by_id.to_string(),
        source_coto_id: ito.source_coto_id.to_string(),
        target_coto_id: ito.target_coto_id.to_string(),
        description: ito.description,
        order: ito.order,
        created_at: ito.created_at.to_string(),
        updated_at: ito.updated_at.to_string(),
    }
}

pub(crate) fn as_db_geolocation(location: &cotoami_plugin_api::Geolocation) -> Geolocation {
    Geolocation::from_lng_lat((location.longitude, location.latitude))
}

pub(crate) fn as_db_coto_input<'a>(
    input: &'a cotoami_plugin_api::CotoInput,
) -> Result<CotoInput<'a>> {
    let media_content = if let Some((content, content_type)) = &input.media_content {
        Some((content.clone().into(), Cow::from(content_type)))
    } else {
        None
    };
    Ok(CotoInput {
        content: Cow::from(&input.content),
        summary: input.summary.as_deref().map(Cow::from),
        media_content,
        geolocation: input.geolocation.as_ref().map(as_db_geolocation),
        datetime_range: None,
    })
}

pub(crate) fn as_db_coto_content_diff(
    diff: cotoami_plugin_api::CotoContentDiff,
) -> CotoContentDiff<'static> {
    let mut db_diff = CotoContentDiff::default();
    if let Some(content) = diff.content {
        db_diff.content = FieldDiff::Change(Cow::from(content));
    }
    if let Some(summary) = diff.summary {
        db_diff.summary = FieldDiff::Change(Cow::from(summary));
    }
    if let Some((content, content_type)) = diff.media_content {
        db_diff.media_content =
            FieldDiff::Change((content.clone().into(), Cow::from(content_type)));
    }
    if let Some(location) = diff.geolocation {
        db_diff.geolocation = FieldDiff::Change(as_db_geolocation(&location));
    }
    db_diff
}

pub(crate) fn as_db_ito_input<'a>(input: &'a cotoami_plugin_api::ItoInput) -> Result<ItoInput<'a>> {
    Ok(ItoInput {
        source_coto_id: Id::from_str(&input.source_coto_id)?,
        target_coto_id: Id::from_str(&input.target_coto_id)?,
        description: input.description.as_deref().map(Cow::from),
        details: None,
        order: None,
    })
}
