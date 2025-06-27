use cotoami_db::prelude::*;
use cotoami_plugin_api::Event;

pub(crate) fn into_plugin_event(change: Change, local_node_id: Id<Node>) -> Option<Event> {
    let local_node_id = local_node_id.to_string();
    match change {
        Change::CreateCoto(coto) => into_plugin_coto(coto).map(|coto| Event::CotoPosted {
            coto,
            local_node_id,
        }),
        _ => None,
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
            media_content: coto.media_content.map(|bytes| bytes.inner()),
            media_type: coto.media_type,
            is_cotonoma: coto.is_cotonoma,
            longitude: coto.longitude,
            latitude: coto.latitude,
            datetime_start: coto.datetime_start.map(|time| time.to_string()),
            datetime_end: coto.datetime_end.map(|time| time.to_string()),
            repost_of_id: coto.repost_of_id.map(|id| id.to_string()),
            created_at: coto.created_at.to_string(),
            updated_at: coto.updated_at.to_string(),
        })
}
